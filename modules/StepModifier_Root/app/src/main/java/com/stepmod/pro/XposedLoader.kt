package com.stepmod.pro

import android.app.Application
import android.util.Log
import com.stepmod.pro.models.StepConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-StepMod-Root"
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
        Log.i(TAG, "StepModifier Pro v$VERSION 初始化 | Root 版 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== StepModifier v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
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
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} 步数修改=${cfg.stepModifyEnabled} " +
                "目标步数=${cfg.customSteps} 波动±${cfg.randomFluctuation} " +
                "[实验]传感器阻断=${cfg.sensorBlockEnabled} 多APP同步=${cfg.multiAppSyncEnabled} 历史伪造=${cfg.stepHistoryFakeEnabled} " +
                "[Root]系统传感器=${cfg.systemSensorEnabled} 健康服务=${cfg.healthServiceEnabled} " +
                "[Root实验]内核注入=${cfg.kernelStepInjectEnabled} Shizuku桥接=${cfg.shizukuStepBridgeEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.stepmod.pro.hooks."

        if (cfg.stepModifyEnabled) {
            tryInvoke(HP + "StepSensorHook", "apply", loader, lpparam, cfg)
            tryInvoke(HP + "StepReportHook", "apply", loader, lpparam, cfg)
            tryInvoke(HP + "StepCounterHook", "apply", loader, lpparam, cfg)
        }

        if (cfg.sensorBlockEnabled) tryInvoke(HP + "SensorBlockHook", "apply", loader, lpparam, cfg)
        if (cfg.multiAppSyncEnabled) tryInvoke(HP + "MultiAppSyncHook", "apply", loader, lpparam, cfg)
        if (cfg.stepHistoryFakeEnabled) tryInvoke(HP + "StepHistoryFakeHook", "apply", loader, lpparam, cfg)

        if (cfg.systemSensorEnabled) tryInvoke(HP + "SystemSensorHook", "apply", loader, lpparam, cfg)
        if (cfg.healthServiceEnabled) tryInvoke(HP + "HealthServiceHook", "apply", loader, lpparam, cfg)

        if (cfg.kernelStepInjectEnabled) tryInvoke(HP + "KernelStepInjectHook", "apply", loader, lpparam, cfg)
        if (cfg.shizukuStepBridgeEnabled) tryInvoke(HP + "ShizukuStepBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.healthDatabaseInjectEnabled) tryInvoke(HP + "HealthDatabaseInjectHook", "apply", loader, lpparam, cfg)

        if (cfg.persistStepInjectEnabled) tryInvoke(HP + "PersistStepInjectHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.stepmod.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.tencent.tim",
        "com.xiaomi.hm.health",
        "com.huawei.health",
        "com.codoon.gps",
        "com.joyrun.gps",
        "com.keepfitness",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.netease.cloudmusic",
        "com.tencent.wmusic",
        "com.taobao.taobao",
        "com.jingdong.app.mall"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.stepmod.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.stepmod.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.stepmod.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): StepConfig {
        try {
            Class.forName("com.stepmod.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as StepConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.stepmod.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? StepConfig ?: StepConfig(packageName = "global")
        } catch (_: Throwable) { StepConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.stepmod.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.stepmod.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.stepmod.pro.utils.LogStore")
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
                            Class.forName("com.stepmod.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.stepmod.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
