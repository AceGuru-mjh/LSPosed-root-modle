package com.batteryopt.pro

import android.app.Application
import android.util.Log
import com.batteryopt.pro.models.BatteryConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-BatteryOpt-Root"
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
        Log.i(TAG, "BatteryOptimizer Pro v$VERSION 初始化 | LSPosed + Shizuku 模式")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== BatteryOptimizer v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
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
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} wakelock=${cfg.wakeLockEnabled} " +
                "alarm=${cfg.alarmEnabled} sync=${cfg.syncEnabled} job=${cfg.jobEnabled} " +
                "location=${cfg.locationEnabled} anim=${cfg.animationEnabled} " +
                "sensor=${cfg.sensorEnabled} " +
                "[实验]bt=${cfg.bluetoothScanThrottleEnabled} cam=${cfg.cameraBackgroundBlockEnabled} " +
                "vib=${cfg.vibratorThrottleEnabled} " +
                "[系统]doze=${cfg.dozeEnabled} freeze=${cfg.freezeEnabled} " +
                "cpu=${cfg.cpuGovernorEnabled} greenify=${cfg.greenifyEnabled} " +
                "[实验-系统]lowpower=${cfg.lowPowerModeAutoEnabled} batreset=${cfg.batteryStatsResetEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.batteryopt.pro.hooks."

        if (cfg.wakeLockEnabled) tryInvoke(HP + "WakeLockHook", "apply", loader, lpparam, cfg)
        if (cfg.alarmEnabled) tryInvoke(HP + "AlarmOptimizerHook", "apply", loader, lpparam, cfg)
        if (cfg.syncEnabled) tryInvoke(HP + "BackgroundSyncHook", "apply", loader, lpparam, cfg)
        if (cfg.jobEnabled) tryInvoke(HP + "JobSchedulerHook", "apply", loader, lpparam, cfg)
        if (cfg.locationEnabled) tryInvoke(HP + "LocationOptHook", "apply", loader, lpparam, cfg)
        if (cfg.animationEnabled) tryInvoke(HP + "AnimationOptHook", "apply", loader, lpparam, cfg)
        if (cfg.sensorEnabled) tryInvoke(HP + "SensorOptHook", "apply", loader, lpparam, cfg)

        if (cfg.bluetoothScanThrottleEnabled) tryInvoke(HP + "BluetoothScanThrottleHook", "apply", loader, lpparam, cfg)
        if (cfg.cameraBackgroundBlockEnabled) tryInvoke(HP + "CameraBackgroundBlockHook", "apply", loader, lpparam, cfg)
        if (cfg.vibratorThrottleEnabled) tryInvoke(HP + "VibratorThrottleHook", "apply", loader, lpparam, cfg)

        tryInvoke(HP + "ShizukuBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.dozeEnabled) tryInvoke(HP + "SystemDozeHook", "apply", loader, lpparam, cfg)
        if (cfg.freezeEnabled) tryInvoke(HP + "BackgroundFreezeHook", "apply", loader, lpparam, cfg)
        if (cfg.cpuGovernorEnabled) tryInvoke(HP + "CpuGovernorHook", "apply", loader, lpparam, cfg)
        if (cfg.greenifyEnabled) tryInvoke(HP + "GreenifyBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.lowPowerModeAutoEnabled) tryInvoke(HP + "LowPowerModeAutoHook", "apply", loader, lpparam, cfg)
        if (cfg.batteryStatsResetEnabled) tryInvoke(HP + "BatteryStatsResetHook", "apply", loader, lpparam, cfg)

        if (cfg.zramOptimizerEnabled) tryInvoke(HP + "ZramOptimizerHook", "apply", loader, lpparam, cfg)
        if (cfg.kernelWakeupEnabled) tryInvoke(HP + "KernelWakeupHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.batteryopt.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.eg.android.AlipayGphone",
        "com.netease.cloudmusic",
        "com.tencent.wmusic",
        "com.zhihu.android",
        "com.sina.weibo",
        "com.netease.mail",
        "com.tencent.androidqqmail"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.batteryopt.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.batteryopt.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.batteryopt.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): BatteryConfig {
        try {
            Class.forName("com.batteryopt.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as BatteryConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.batteryopt.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? BatteryConfig ?: BatteryConfig(packageName = "global")
        } catch (_: Throwable) { BatteryConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.batteryopt.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.batteryopt.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.batteryopt.pro.utils.LogStore")
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
                            Class.forName("com.batteryopt.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.batteryopt.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
