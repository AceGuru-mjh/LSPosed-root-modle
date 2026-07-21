package com.adblockerx.pro

import android.app.Application
import android.util.Log
import com.adblockerx.pro.models.AdBlockConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-AdBlockerX-Root"
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
        Log.i(TAG, "AdBlockerX Pro v$VERSION 初始化 | Root 版 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        currentPkg = pkg
        val local = isLocalMode()
        Log.i(TAG, "=== AdBlockerX v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")

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
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} WebView=${cfg.webviewAdEnabled} OkHttp=${cfg.okHttpAdEnabled} " +
                "URLConnection=${cfg.urlConnectionAdEnabled} Hosts=${cfg.hostsFilterEnabled} AdView=${cfg.adViewHideEnabled} " +
                "[实验]Tracker=${cfg.trackerBlockEnabled} Cookie=${cfg.cookieCleanEnabled} " +
                "Redirect=${cfg.redirectBlockEnabled} Intent=${cfg.intentInterceptorEnabled} " +
                "[Root]SystemHosts=${cfg.systemHostsEnabled} PrivateDns=${cfg.privateDnsEnabled} " +
                "DnsResolver=${cfg.dnsResolverHookEnabled} ShizukuBridge=${cfg.shizukuBridgeEnabled} " +
                "[Root实验]Iptables=${cfg.iptablesBlockEnabled} VPN=${cfg.vpnBasedBlockEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.adblockerx.pro.hooks."

        if (cfg.hostsFilterEnabled) tryInvoke(HP + "HostsFilterHook", "apply", loader, lpparam, cfg)

        if (cfg.webviewAdEnabled) tryInvoke(HP + "WebViewAdHook", "apply", loader, lpparam, cfg)
        if (cfg.okHttpAdEnabled) tryInvoke(HP + "OkHttpAdHook", "apply", loader, lpparam, cfg)
        if (cfg.urlConnectionAdEnabled) tryInvoke(HP + "URLConnectionAdHook", "apply", loader, lpparam, cfg)
        if (cfg.adViewHideEnabled) tryInvoke(HP + "AdViewHideHook", "apply", loader, lpparam, cfg)

        if (cfg.trackerBlockEnabled) tryInvoke(HP + "TrackerBlockHook", "apply", loader, lpparam, cfg)
        if (cfg.cookieCleanEnabled) tryInvoke(HP + "CookieCleanHook", "apply", loader, lpparam, cfg)
        if (cfg.redirectBlockEnabled) tryInvoke(HP + "RedirectBlockHook", "apply", loader, lpparam, cfg)
        if (cfg.intentInterceptorEnabled) tryInvoke(HP + "IntentInterceptorHook", "apply", loader, lpparam, cfg)

        if (cfg.screenshotUnlockEnabled || cfg.shakeAdBlockEnabled || cfg.vpnDetectBypassEnabled) {
            tryInvoke(HP + "AdClosePlusHook", "apply", loader, lpparam, cfg)
        }

        if (cfg.systemHostsEnabled) tryInvoke(HP + "SystemHostsHook", "apply", loader, lpparam, cfg)
        if (cfg.privateDnsEnabled) tryInvoke(HP + "PrivateDnsHook", "apply", loader, lpparam, cfg)
        if (cfg.dnsResolverHookEnabled) tryInvoke(HP + "DnsResolverHook", "apply", loader, lpparam, cfg)
        if (cfg.shizukuBridgeEnabled) tryInvoke(HP + "ShizukuBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.iptablesBlockEnabled) tryInvoke(HP + "IptablesBlockHook", "apply", loader, lpparam, cfg)
        if (cfg.vpnBasedBlockEnabled) tryInvoke(HP + "VpnBasedBlockHook", "apply", loader, lpparam, cfg)

        if (cfg.dnsCacheFlushEnabled) tryInvoke(HP + "DnsCacheFlushHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.adblockerx.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.android.chrome",
        "com.mi.globalbrowser",
        "com.huawei.browser",
        "com.sec.android.app.sbrowser",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.eg.android.AlipayGphone",
        "com.zhihu.android",
        "com.netease.cloudmusic",
        "com.tencent.wmusic"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.adblockerx.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.adblockerx.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.adblockerx.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): AdBlockConfig {
        try {
            Class.forName("com.adblockerx.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as AdBlockConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.adblockerx.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? AdBlockConfig ?: AdBlockConfig()
        } catch (_: Throwable) { AdBlockConfig() }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.adblockerx.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.adblockerx.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.adblockerx.pro.utils.LogStore")
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
                            Class.forName("com.adblockerx.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.adblockerx.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
