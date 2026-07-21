package com.videosaver.pro.hooks

import android.app.Application
import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统下载服务 Hook（Root 专属�?
 *
 * 实现思路�?
 *  - Hook Application.onCreate �?APP 启动时初始化
 *  - Hook 视频下载入口方法（如 downloadVideo(String)），通过 Shizuku 调用系统下载服务
 *  - Shizuku 执行 `am start -a android.intent.action.VIEW -d <url> -t video/mp4` 触发系统下载
 *  - 或通过 `cmd download` 调用 DownloadManager（仅 Android 10+�?
 *
 * 硬性限制：
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - 不调�?su，所有系统操作走 Shizuku 反射
 *  - catch 块使�?`catch (_: Throwable) {}` 静默处理
 */
object SystemDownloadHook {

    private val DOWNLOAD_ENTRY_CANDIDATES = arrayOf(
        "com.ss.android.ugc.aweme.services.video.IDownloadService",
        "com.yxcorp.gifshow.download.DownloadManager",
        "com.xingin.xhs.download.NoteDownloadManager",
        "tv.danmaku.bili.download.VideoDownloadHelper"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.systemDownloadEnabled) return
        LogX.i("系统下载服务 Hook 启动（Root 专属�?)

        hookAppLifecycle(lpparam, cfg)
        hookDownloadEntries(lpparam, cfg)
    }

    /** Hook Application.onCreate 触发 Shizuku 检测与初始�?*/
    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            if (ShizukuHelper.isShizukuAvailable()) {
                                LogX.i("Shizuku 可用，系统下载服务就�?)
                            } else {
                                LogX.w("Shizuku 不可用，系统下载功能受限")
                            }
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(SystemDownload)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate", e)
        }
    }

    /** Hook 视频下载入口，通过 Shizuku 启动系统下载服务 */
    private fun hookDownloadEntries(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in DOWNLOAD_ENTRY_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // �?String 参数（视�?URL�?
                try {
                    XposedHelpers.findAndHookMethod(cls, "download",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                triggerSystemDownload(url, cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "download(SystemDownload)")
                } catch (_: Throwable) { }
                // �?String 参数（URL + 文件名）
                try {
                    XposedHelpers.findAndHookMethod(cls, "download",
                        String::class.java, String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                triggerSystemDownload(url, cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "download(url,name)")
                } catch (_: Throwable) { }
                try {
                    XposedHelpers.findAndHookMethod(cls, "startDownload",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                triggerSystemDownload(url, cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "startDownload(SystemDownload)")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** 通过 Shizuku 启动系统下载（am start VIEW �?cmd download�?*/
    private fun triggerSystemDownload(url: String, cfg: VideoConfig) {
        if (url.isBlank()) return
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过系统下载")
            return
        }
        Thread {
            try {
                // 方式 1：通过 am start VIEW Intent 触发系统下载�?
                val fileName = "video_${System.currentTimeMillis()}.mp4"
                val cmd = if (cfg.useSystemDownloadNotification) {
                    // 方式 2：使�?cmd download（Android 10+ 系统下载命令�?
                    "cmd download \"$url\" \"$fileName\""
                } else {
                    "am start -a android.intent.action.VIEW -d \"$url\" -t video/mp4"
                }
                val ok = ShizukuHelper.execShellSilent(cmd)
                LogX.i("系统下载触发: $url (success=$ok)")
            } catch (_: Throwable) { }
        }.start()
    }

    fun release() {
        ShizukuHelper.release()
    }
}
