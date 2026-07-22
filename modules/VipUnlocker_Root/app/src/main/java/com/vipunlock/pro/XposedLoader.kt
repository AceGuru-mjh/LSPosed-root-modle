package com.vipunlock.pro

import android.app.Application
import android.util.Log
import com.vipunlock.pro.models.VipConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-VipUnlocker-Root"
        const val VERSION = "1.0.13"
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
        Log.i(TAG, "VipUnlocker Pro v$VERSION 初始化 | Root 版 | LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== VipUnlocker v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
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
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} 网易=${cfg.netEaseVipEnabled} QQ音乐=${cfg.qqMusicVipEnabled} " +
                "爱奇艺=${cfg.iqiyiVipEnabled} B站=${cfg.biliVipEnabled} 知乎=${cfg.zhihuVipEnabled} " +
                "[实验]通用VIP=${cfg.universalVipTryEnabled} 去广告=${cfg.removeAdsEnabled} 绕过校验=${cfg.bypassVerifyEnabled} " +
                "[Root]系统属性伪装=${cfg.systemPropVipEnabled} License=${cfg.licenseVerifyEnabled} " +
                "[Root实验]Shizuku桥接=${cfg.shizukuVipBridgeEnabled} 全局广告屏蔽=${cfg.globalAdBlockEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.vipunlock.pro.hooks."

        if (cfg.netEaseVipEnabled && pkg == "com.netease.cloudmusic") tryInvoke(HP + "NetEaseMusicVipHook", "apply", loader, lpparam, cfg)
        if (cfg.qqMusicVipEnabled && pkg == "com.tencent.wmusic") tryInvoke(HP + "QQMusicVipHook", "apply", loader, lpparam, cfg)
        if (cfg.kugouVipEnabled && pkg == "com.kugou.android") tryInvoke(HP + "UniversalVipHook", "applyForKugou", loader, lpparam, cfg)
        if (cfg.kuwoVipEnabled && pkg == "com.kuwo.player") tryInvoke(HP + "UniversalVipHook", "applyForKuwo", loader, lpparam, cfg)

        if (cfg.iqiyiVipEnabled && pkg == "com.qiyi.video") tryInvoke(HP + "IqiyiVipHook", "apply", loader, lpparam, cfg)
        if (cfg.youkuVipEnabled && pkg == "com.youku.phone") tryInvoke(HP + "UniversalVipHook", "applyForYouku", loader, lpparam, cfg)
        if (cfg.tencentVideoVipEnabled && pkg == "com.tencent.qqlive") tryInvoke(HP + "UniversalVipHook", "applyForTencentVideo", loader, lpparam, cfg)
        if (cfg.biliVipEnabled && pkg == "tv.danmaku.bili") tryInvoke(HP + "BilibiliVipHook", "apply", loader, lpparam, cfg)

        if (cfg.ximalayaVipEnabled && pkg == "com.ximalaya.ting.android") tryInvoke(HP + "UniversalVipHook", "applyForXimalaya", loader, lpparam, cfg)
        if (cfg.toutiaoVipEnabled && pkg == "com.ss.android.article.news") tryInvoke(HP + "UniversalVipHook", "applyForToutiao", loader, lpparam, cfg)
        if (cfg.zhihuVipEnabled && pkg == "com.zhihu.android") tryInvoke(HP + "UniversalVipHook", "applyForZhihu", loader, lpparam, cfg)

        if (cfg.baiduNetdiskVipEnabled && pkg == "com.baidu.netdisk") tryInvoke(HP + "UniversalVipHook", "applyForBaiduNetdisk", loader, lpparam, cfg)
        if (cfg.wpsVipEnabled && pkg == "com.wps.moffice_eng") tryInvoke(HP + "UniversalVipHook", "applyForWps", loader, lpparam, cfg)
        if (cfg.wereadVipEnabled && pkg == "com.tencent.weread") tryInvoke(HP + "UniversalVipHook", "applyForWeread", loader, lpparam, cfg)

        if (cfg.universalVipTryEnabled) tryInvoke(HP + "UniversalVipHook", "applyForCommon", loader, lpparam, cfg)
        if (cfg.removeAdsEnabled) tryInvoke(HP + "RemoveAdsHook", "apply", loader, lpparam, cfg)
        if (cfg.bypassVerifyEnabled) tryInvoke(HP + "BypassVerifyHook", "apply", loader, lpparam, cfg)

        if (cfg.systemPropVipEnabled) tryInvoke(HP + "SystemPropVipHook", "apply", loader, lpparam, cfg)
        if (cfg.licenseVerifyEnabled) tryInvoke(HP + "LicenseVerifyHook", "apply", loader, lpparam, cfg)

        if (cfg.shizukuVipBridgeEnabled) tryInvoke(HP + "ShizukuVipBridgeHook", "apply", loader, lpparam, cfg)
        if (cfg.globalAdBlockEnabled) tryInvoke(HP + "GlobalAdBlockHook", "apply", loader, lpparam, cfg)

        if (cfg.persistentVipEnabled) tryInvoke(HP + "PersistentVipHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.vipunlock.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.netease.cloudmusic", "com.tencent.wmusic", "com.kugou.android", "com.kuwo.player",
        "com.qiyi.video", "com.youku.phone", "com.tencent.qqlive", "tv.danmaku.bili",
        "com.ximalaya.ting.android", "com.ss.android.article.news", "com.zhihu.android",
        "com.baidu.netdisk", "com.wps.moffice_eng", "com.tencent.weread",
        "com.sdu.didi.psnger", "com.eg.android.AlipayGphone"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.vipunlock.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.vipunlock.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.vipunlock.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): VipConfig {
        try {
            Class.forName("com.vipunlock.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as VipConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.vipunlock.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? VipConfig ?: VipConfig(packageName = "global")
        } catch (_: Throwable) { VipConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.vipunlock.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.vipunlock.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.vipunlock.pro.utils.LogStore")
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
                            Class.forName("com.vipunlock.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.vipunlock.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
