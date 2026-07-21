package com.batteryopt.pro

import android.app.Application
import com.batteryopt.pro.hooks.*
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.ConfigManager
import com.batteryopt.pro.utils.HookConfigReader
import com.batteryopt.pro.utils.LogStore
import com.batteryopt.pro.utils.AntiDetectionHelper
import com.batteryopt.pro.utils.EnvDetector
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ModuleConflictDetector
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * BatteryOptimizer Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit�?
 *
 * 工作流程�?
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [A] 应用�?Hook�?个）：WakeLock / Alarm / Sync / Job / Location / Animation / Sensor
 *    [A-实验] 蓝牙扫描 / 相机阻断 / 振动�?
 *    [B] 系统�?Hook（需 Shizuku）：
 *        SystemDoze / BackgroundFreeze / CpuGovernor / GreenifyBridge / ShizukuBridge
 *    [B-实验] LowPowerModeAuto / BatteryStatsReset
 *
 * 系统�?Hook 注意事项�?
 *  - 必须先检�?Shizuku 可用性（ShizukuBridgeHook 统一检测）
 *  - 屏幕开关广播由各系统级 Hook 自行注册监听
 *  - 系统 Doze/冻结/CPU 仅在屏幕关闭时触�?
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("BatteryOptimizer Pro v$VERSION 初始�?| LSPosed + Shizuku 模式")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("=== BatteryOptimizer v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (EnvDetector.isLocalMode) "local" else "integrated"} ===")
        currentPkg = pkg

        LogX.i("环境: ${if (EnvDetector.isLocalMode) "本地模式" else "集成模式"}")
        if (ModuleConflictDetector.checkConflict(lpparam)) {
            LogX.w("检测到模块冲突，部分功能已禁用")
            LogStore.add("warn", "模块冲突检测触�?)
        }

        initConfig(lpparam)
        if (!EnvDetector.isLocalMode) {
            try { Thread.sleep(100) } catch (_: Throwable) { }
        }

        val cfg = loadConfig()
        LogX.i("配置: 总开�?${cfg.masterEnabled} wakelock=${cfg.wakeLockEnabled} " +
                "alarm=${cfg.alarmEnabled} sync=${cfg.syncEnabled} job=${cfg.jobEnabled} " +
                "location=${cfg.locationEnabled} anim=${cfg.animationEnabled} " +
                "sensor=${cfg.sensorEnabled} " +
                "[实验]bt=${cfg.bluetoothScanThrottleEnabled} cam=${cfg.cameraBackgroundBlockEnabled} " +
                "vib=${cfg.vibratorThrottleEnabled} " +
                "[系统]doze=${cfg.dozeEnabled} freeze=${cfg.freezeEnabled} " +
                "cpu=${cfg.cpuGovernorEnabled} greenify=${cfg.greenifyEnabled} " +
                "[实验-系统]lowpower=${cfg.lowPowerModeAutoEnabled} batreset=${cfg.batteryStatsResetEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== [A] 应用�?Hook =====
        if (cfg.wakeLockEnabled) WakeLockHook.apply(lpparam, cfg)
        if (cfg.alarmEnabled) AlarmOptimizerHook.apply(lpparam, cfg)
        if (cfg.syncEnabled) BackgroundSyncHook.apply(lpparam, cfg)
        if (cfg.jobEnabled) JobSchedulerHook.apply(lpparam, cfg)
        if (cfg.locationEnabled) LocationOptHook.apply(lpparam, cfg)
        if (cfg.animationEnabled) AnimationOptHook.apply(lpparam, cfg)
        if (cfg.sensorEnabled) SensorOptHook.apply(lpparam, cfg)

        // ===== [A-实验] 应用层实验�?=====
        if (cfg.bluetoothScanThrottleEnabled) BluetoothScanThrottleHook.apply(lpparam, cfg)
        if (cfg.cameraBackgroundBlockEnabled) CameraBackgroundBlockHook.apply(lpparam, cfg)
        if (cfg.vibratorThrottleEnabled) VibratorThrottleHook.apply(lpparam, cfg)

        // ===== [B] 系统�?Hook（需 Shizuku�?====
        ShizukuBridgeHook.apply(lpparam, cfg)

        if (cfg.dozeEnabled) SystemDozeHook.apply(lpparam, cfg)
        if (cfg.freezeEnabled) BackgroundFreezeHook.apply(lpparam, cfg)
        if (cfg.cpuGovernorEnabled) CpuGovernorHook.apply(lpparam, cfg)
        if (cfg.greenifyEnabled) GreenifyBridgeHook.apply(lpparam, cfg)

        // ===== [B-实验] 系统级实验�?=====
        if (cfg.lowPowerModeAutoEnabled) LowPowerModeAutoHook.apply(lpparam, cfg)
        if (cfg.batteryStatsResetEnabled) BatteryStatsResetHook.apply(lpparam, cfg)

        // ===== [Task24] 系统级增�?=====
        if (cfg.zramOptimizerEnabled) ZramOptimizerHook.apply(lpparam, cfg)
        if (cfg.kernelWakeupEnabled) KernelWakeupHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            AntiDetectionHelper.sleepDuringVerify()
        }
    }

    /** 目标耗电大户 APP 包名白名�?*/
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

    private fun loadConfig(): BatteryConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { BatteryConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        EnvDetector.detect(lpparam)
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) { ConfigManager.init(app); LogStore.init(app) }
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app); LogStore.init(app) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
