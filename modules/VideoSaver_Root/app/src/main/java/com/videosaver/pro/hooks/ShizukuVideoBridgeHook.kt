package com.videosaver.pro.hooks

import android.app.Application
import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 视频桥接 Hook（Root 专属�?
 *
 * 实现思路�?
 *  - Hook Application.onCreate 注册广播接收�?
 *  - Hook 视频分享方法，通过 Shizuku 执行 `am broadcast -a <action> --es url <url> --es name <name>`
 *  - 模块自身进程可注册接收器并下载（或由其他模块接收�?
 *
 * 硬性限制：
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - 不调�?su，所有系统操作走 Shizuku 反射
 *  - catch 块使�?`catch (_: Throwable) {}` 静默处理
 */
object ShizukuVideoBridgeHook {

    private val SHARE_ENTRY_CANDIDATES = arrayOf(
        "com.ss.android.ugc.aweme.share.SharePackage",
        "com.yxcorp.gifshow.share.SharePackage",
        "com.xingin.xhs.share.NoteShareHelper",
        "tv.danmaku.bili.share.ShareHelper"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.shizukuVideoBridgeEnabled) return
        LogX.i("Shizuku 视频桥接 Hook 启动（Root 专属�?)

        hookAppLifecycle(lpparam, cfg)
        hookShareEntries(lpparam, cfg)
    }

    /** Hook Application.onCreate 触发 Shizuku 检�?*/
    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            if (ShizukuHelper.isShizukuAvailable()) {
                                LogX.i("Shizuku 视频桥接就绪")
                            } else {
                                LogX.w("Shizuku 不可用，视频桥接受限")
                            }
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(ShizukuVideoBridge)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate", e)
        }
    }

    /** Hook 视频分享入口，通过 Shizuku am broadcast 触发下载 */
    private fun hookShareEntries(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in SHARE_ENTRY_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // share(String url, String title)
                try {
                    XposedHelpers.findAndHookMethod(cls, "share",
                        String::class.java, String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.getOrNull(0) as? String ?: return
                                val name = (p.args.getOrNull(1) as? String) ?: "video"
                                triggerBroadcast(url, name, cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "share(url,title)")
                } catch (_: Throwable) { }
                // shareImpl(String)
                try {
                    XposedHelpers.findAndHookMethod(cls, "shareImpl",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                triggerBroadcast(url, "video", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "shareImpl")
                } catch (_: Throwable) { }
                // startShare(String)
                try {
                    XposedHelpers.findAndHookMethod(cls, "startShare",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                triggerBroadcast(url, "video", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "startShare")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 通过 Shizuku 执行 am broadcast */
    private fun triggerBroadcast(url: String, name: String, cfg: VideoConfig) {
        if (url.isBlank()) return
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过广播")
            return
        }
        val action = cfg.broadcastAction.ifBlank { "com.videosaver.pro.ACTION_DOWNLOAD" }
        Thread {
            try {
                val ok = ShizukuHelper.broadcast(action, "url" to url, "name" to name)
                LogX.i("Shizuku 广播触发: $action url=$url (success=$ok)")
            } catch (_: Throwable) { }
        }.start()
    }

    fun release() {
        ShizukuHelper.release()
    }
}
