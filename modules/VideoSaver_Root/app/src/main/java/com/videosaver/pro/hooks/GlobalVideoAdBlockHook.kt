package com.videosaver.pro.hooks

import android.app.Application
import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】全局视频广告屏蔽 Hook（Root 专属�?
 *
 * 实现思路�?
 *  - Hook Application.onCreate �?APP 启动时通过 Shizuku 修改 /etc/hosts �?/system/etc/hosts
 *  - 将配置中的广告域名映射到 127.0.0.1，实现全局屏蔽
 *  - 比应用层 RemoveVideoAdsHook 更彻底，影响所有网络请�?
 *
 * 硬性限制：
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - 修改 hosts 需�?root 级别 Shizuku 授权（非 adb 级）
 *  - /etc/hosts 通常为只读，需 remount �?Magisk overlay
 *  - catch 块使�?`catch (_: Throwable) {}` 静默处理
 *  - 实验性，可能导致部分 APP 网络异常
 */
object GlobalVideoAdBlockHook {

    /** hosts 文件路径候�?*/
    private val HOSTS_PATH_CANDIDATES = arrayOf(
        "/system/etc/hosts",
        "/etc/hosts",
        "/data/adb/modules/videosaver/system/etc/hosts"  // Magisk overlay 路径
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.globalVideoAdBlockEnabled) return
        LogX.i("【实验性】全局视频广告屏蔽 Hook 启动（Root 专属�?)

        hookAppLifecycle(lpparam, cfg)
        hookNetworkRequest(lpparam, cfg)
    }

    /** Hook Application.onCreate 触发 hosts 修改 */
    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            modifyHosts(cfg)
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(GlobalAdBlock)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate", e)
        }
    }

    /** Hook 网络 DNS 解析方法，应用层兜底屏蔽（hosts 修改失败时） */
    private fun hookNetworkRequest(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            val inetAddressCls = XposedHelpers.findClassIfExists(
                "java.net.InetAddress", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(inetAddressCls, "getAllByName",
                    String::class.java, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val host = p.args.firstOrNull() as? String ?: return
                                if (isAdDomain(host, cfg)) {
                                    LogX.d("广告域名 DNS 解析拦截: $host")
                                    // �?UnknownHostException 阻断解析
                                    throw java.net.UnknownHostException("Blocked by VideoSaver: $host")
                                }
                            } catch (_: Throwable) { }
                        }
                    })
                LogX.hookSuccess("InetAddress", "getAllByName")
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    /** 通过 Shizuku 修改 hosts 文件 */
    private fun modifyHosts(cfg: VideoConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 hosts 修改")
            return
        }
        if (cfg.adBlockHosts.isEmpty()) {
            LogX.w("未配置广告域名，跳过 hosts 修改")
            return
        }
        Thread {
            try {
                // 读取当前 hosts 内容
                val currentHosts = ShizukuHelper.readFile("/system/etc/hosts") ?: ""
                // 构造新�?hosts 内容（追加屏蔽条目）
                val sb = StringBuilder(currentHosts)
                for (host in cfg.adBlockHosts) {
                    if (!currentHosts.contains(host)) {
                        sb.append("\n127.0.0.1 $host")
                    }
                }
                val newHosts = sb.toString()
                // 尝试写入 hosts 文件
                for (path in HOSTS_PATH_CANDIDATES) {
                    val ok = ShizukuHelper.writeFile(path, newHosts)
                    if (ok) {
                        LogX.i("hosts 文件已修�? $path")
                        return@Thread
                    }
                }
                LogX.w("所�?hosts 路径写入失败（可能需�?root �?Shizuku �?Magisk overlay�?)
            } catch (_: Throwable) { }
        }.start()
    }

    /** 判断 host 是否在广告屏蔽列�?*/
    private fun isAdDomain(host: String, cfg: VideoConfig): Boolean {
        if (host.isBlank()) return false
        return cfg.adBlockHosts.any { host.contains(it, ignoreCase = true) }
    }

    fun release() {
        ShizukuHelper.release()
    }
}
