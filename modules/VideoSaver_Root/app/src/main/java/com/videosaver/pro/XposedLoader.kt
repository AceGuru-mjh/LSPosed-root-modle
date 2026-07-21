package com.videosaver.pro

import android.app.Application
import android.util.Log
import com.videosaver.pro.models.VideoConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-VideoSaver-Root"
        const val VERSION = "1.0.12"
        var currentPkg: String? = null
    }

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: Any) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, cfg.javaClass)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        Log.i(TAG, "VideoSaver Pro v$VERSION 初始化 | Root 版")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== VideoSaver v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
        currentPkg = pkg

        Log.i(TAG, "环境: ${if (local) "本地模式" else "集成模式"}")
        if (checkConflict(lpparam)) {
            Log.w(TAG, "检测到模块冲突，部分功能已禁用")
            addLogStore("warn", "模块冲突检测触发")
        }

        initConfig(lpparam)
        if (!local) {
            try { Thread.sleep(100) } catch (_: Throwable) { }
        }

        val cfg = loadConfig()
        cfg.packageName = pkg
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} 抖音=${cfg.douyinNoWatermark} " +
                "快手=${cfg.kuaishouNoWatermark} 小红书=${cfg.xhsNoWatermark} B站=${cfg.biliDownload} " +
                "[实验]自动下载=${cfg.autoDownloadEnabled} 去广告=${cfg.removeAdsEnabled} " +
                "原画质=${cfg.saveOriginalQualityEnabled} 批量下载=${cfg.batchDownloadEnabled} " +
                "[Root]系统下载=${cfg.systemDownloadEnabled} Shizuku桥接=${cfg.shizukuVideoBridgeEnabled} " +
                "[Root实验]全局广告=${cfg.globalVideoAdBlockEnabled} 内核增强=${cfg.kernelVideoEnhanceEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.videosaver.pro.hooks."

        if (cfg.douyinNoWatermark) tryInvoke(HP + "DouyinNoWatermarkHook", "apply", loader, lpparam, cfg)
        if (cfg.kuaishouNoWatermark) tryInvoke(HP + "KuaishouNoWatermarkHook", "apply", loader, lpparam, cfg)
        if (cfg.xhsNoWatermark) tryInvoke(HP + "XhsNoWatermarkHook", "apply", loader, lpparam, cfg)
        if (cfg.biliDownload) tryInvoke(HP + "BiliDownloadHook", "apply", loader, lpparam, cfg)

        if (cfg.autoDownloadEnabled) tryInvoke(HP + "AutoDownloadHook", "apply", loader, lpparam, cfg)
        if (cfg.removeAdsEnabled) tryInvoke(HP + "RemoveVideoAdsHook", "apply", loader, lpparam, cfg)
        if (cfg.saveOriginalQualityEnabled) tryInvoke(HP + "SaveOriginalQualityHook", "apply", loader, lpparam, cfg)
        if (cfg.batchDownloadEnabled) tryInvoke(HP + "BatchDownloadHook", "apply", loader, lpparam, cfg)

        if (cfg.systemDownloadEnabled) tryInvoke(HP + "SystemDownloadHook", "apply", loader, lpparam, cfg)
        if (cfg.shizukuVideoBridgeEnabled) tryInvoke(HP + "ShizukuVideoBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.globalVideoAdBlockEnabled) tryInvoke(HP + "GlobalVideoAdBlockHook", "apply", loader, lpparam, cfg)
        if (cfg.kernelVideoEnhanceEnabled) tryInvoke(HP + "KernelVideoEnhanceHook", "apply", loader, lpparam, cfg)

        if (cfg.mediaScannerEnabled) tryInvoke(HP + "MediaScannerHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.videosaver.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
        "com.smile.gifmaker",
        "com.kuaishou.nebula",
        "com.xingin.xhs",
        "com.xingin.xhscircle",
        "tv.danmaku.bili",
        "com.tencent.qqlive",
        "com.ss.android.article.video",
        "com.hihonor.cloudmusic"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.videosaver.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.videosaver.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.videosaver.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): VideoConfig {
        try {
            Class.forName("com.videosaver.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as VideoConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.videosaver.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? VideoConfig ?: VideoConfig(packageName = "global")
        } catch (_: Throwable) { VideoConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.videosaver.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.videosaver.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.videosaver.pro.utils.LogStore")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
            }
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            Class.forName("com.videosaver.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.videosaver.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
