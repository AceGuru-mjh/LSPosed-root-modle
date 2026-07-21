package com.privacyguard.pro

import android.app.Application
import android.util.Log
import com.privacyguard.pro.models.PrivacyConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-PrivacyGuard-Root"
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
        Log.i(TAG, "PrivacyGuard Pro v$VERSION 初始化 | Root 版 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== PrivacyGuard v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
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
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} 设备ID=${cfg.deviceIdSpoofEnabled} " +
                "剪贴板=${cfg.clipboardGuardEnabled} 位置=${cfg.locationSpoofEnabled} " +
                "[实验]包可见=${cfg.packageVisibilitySpoofEnabled} 网络=${cfg.networkInfoSpoofEnabled} " +
                "[Root]系统属性=${cfg.systemPropSpoofEnabled} 全局权限=${cfg.globalPermissionHookEnabled} " +
                "网络标识=${cfg.networkIdentifierHookEnabled} Shizuku桥接=${cfg.shizukuBridgeEnabled} " +
                "[Root实验]SELinux=${cfg.selinuxContextSpoofEnabled} Cmdline=${cfg.kernelCmdlineHideEnabled} " +
                "[挂载]CmdlineMount=${cfg.kernelCmdlineMountEnabled} SePolicy=${cfg.selinuxPolicyEnabled} ProcMounts=${cfg.procMountsHideEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.privacyguard.pro.hooks."

        if (cfg.deviceIdSpoofEnabled) tryInvoke(HP + "DeviceIdSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.clipboardGuardEnabled) tryInvoke(HP + "ClipboardGuardHook", "apply", loader, lpparam, cfg)
        if (cfg.permissionSpoofEnabled) tryInvoke(HP + "PermissionSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.locationSpoofEnabled) tryInvoke(HP + "LocationSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.sensorFakerEnabled) tryInvoke(HP + "SensorFakerHook", "apply", loader, lpparam, cfg)
        if (cfg.advertisingIdBlockEnabled) tryInvoke(HP + "AdvertisingIdHook", "apply", loader, lpparam, cfg)

        if (cfg.packageVisibilitySpoofEnabled) tryInvoke(HP + "PackageVisibilitySpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.networkInfoSpoofEnabled) tryInvoke(HP + "NetworkInfoSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.screenMetricsSpoofEnabled) tryInvoke(HP + "ScreenMetricsSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.storagePathSpoofEnabled) tryInvoke(HP + "StoragePathSpoofHook", "apply", loader, lpparam, cfg)

        if (cfg.installStatusSpoofEnabled || cfg.mockLocationSystemLevelEnabled) {
            tryInvoke(HP + "PrivacyPlusHook", "apply", loader, lpparam, cfg)
        }

        if (cfg.systemPropSpoofEnabled) tryInvoke(HP + "SystemPropSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.globalPermissionHookEnabled) tryInvoke(HP + "GlobalPermissionHook", "apply", loader, lpparam, cfg)
        if (cfg.networkIdentifierHookEnabled) tryInvoke(HP + "NetworkIdentifierHook", "apply", loader, lpparam, cfg)
        if (cfg.shizukuBridgeEnabled) tryInvoke(HP + "ShizukuBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.selinuxContextSpoofEnabled) tryInvoke(HP + "SelinuxContextSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.kernelCmdlineHideEnabled) tryInvoke(HP + "KernelCmdlineHideHook", "apply", loader, lpparam, cfg)

        if (cfg.buildPropSpoofEnabled) tryInvoke(HP + "BuildPropSpoofHook", "apply", loader, lpparam, cfg)
        if (cfg.procHideEnabled) tryInvoke(HP + "ProcHideHook", "apply", loader, lpparam, cfg)

        if (cfg.kernelCmdlineMountEnabled) tryInvoke(HP + "KernelCmdlineHideHook", "applyShizukuMount", loader, lpparam, cfg)
        if (cfg.selinuxPolicyEnabled) tryInvoke(HP + "SelinuxContextSpoofHook", "applySePolicy", loader, lpparam, cfg)
        if (cfg.procMountsHideEnabled) tryInvoke(HP + "ProcMountsHideHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.privacyguard.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone", "com.taobao.taobao",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.sina.weibo", "com.xunmeng.pinduoduo",
        "com.jingdong.app.mall", "com.android.chrome",
        "com.mi.globalbrowser", "com.zhihu.android",
        "com.netease.cloudmusic"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.privacyguard.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.privacyguard.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.privacyguard.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): PrivacyConfig {
        try {
            Class.forName("com.privacyguard.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as PrivacyConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.privacyguard.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? PrivacyConfig ?: PrivacyConfig(packageName = "global")
        } catch (_: Throwable) { PrivacyConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.privacyguard.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.privacyguard.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.privacyguard.pro.utils.LogStore")
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
                            Class.forName("com.privacyguard.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.privacyguard.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
