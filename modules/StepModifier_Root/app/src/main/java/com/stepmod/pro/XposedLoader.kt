package com.stepmod.pro

import android.app.Application
import com.stepmod.pro.hooks.*
import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.ConfigManager
import com.stepmod.pro.utils.HookConfigReader
import com.stepmod.pro.utils.LogStore
import com.stepmod.pro.utils.AntiDetectionHelper
import com.stepmod.pro.utils.EnvDetector
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ModuleConflictDetector
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * StepModifier Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit�?
 *
 * 工作流程�?
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [基础] StepSensorHook / StepReportHook / StepCounterHook
 *    [实验] SensorBlockHook / MultiAppSyncHook / StepHistoryFakeHook
 *    [Root] SystemSensorHook / HealthServiceHook
 *    [Root 实验] KernelStepInjectHook / ShizukuStepBridgeHook
 *
 * 硬性限制：
 *  - Root 系统�?Hook 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - 系统�?Hook 失败时降级为应用�?Hook
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.12"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("StepModifier Pro v$VERSION 初始�?| Root �?| LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("=== StepModifier v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (EnvDetector.isLocalMode) "local" else "integrated"} ===")
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
        LogX.i("配置: 总开�?${cfg.masterEnabled} 步数修改=${cfg.stepModifyEnabled} " +
                "目标步数=${cfg.customSteps} 波动±${cfg.randomFluctuation} " +
                "[实验]传感器阻�?${cfg.sensorBlockEnabled} 多APP同步=${cfg.multiAppSyncEnabled} 历史伪�?${cfg.stepHistoryFakeEnabled} " +
                "[Root]系统传感�?${cfg.systemSensorEnabled} 健康服务=${cfg.healthServiceEnabled} " +
                "[Root实验]内核注入=${cfg.kernelStepInjectEnabled} Shizuku桥接=${cfg.shizukuStepBridgeEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能（同 NoRoot�?=====
        if (cfg.stepModifyEnabled) {
            StepSensorHook.apply(lpparam, cfg)
            StepReportHook.apply(lpparam, cfg)
            StepCounterHook.apply(lpparam, cfg)
        }

        // ===== 应用层实验性（�?NoRoot�?=====
        if (cfg.sensorBlockEnabled) SensorBlockHook.apply(lpparam, cfg)
        if (cfg.multiAppSyncEnabled) MultiAppSyncHook.apply(lpparam, cfg)
        if (cfg.stepHistoryFakeEnabled) StepHistoryFakeHook.apply(lpparam, cfg)

        // ===== Root 专属：系统级 Hook（需 Shizuku�?=====
        if (cfg.systemSensorEnabled) SystemSensorHook.apply(lpparam, cfg)
        if (cfg.healthServiceEnabled) HealthServiceHook.apply(lpparam, cfg)

        // ===== Root 实验�?=====
        if (cfg.kernelStepInjectEnabled) KernelStepInjectHook.apply(lpparam, cfg)
        if (cfg.shizukuStepBridgeEnabled) ShizukuStepBridgeHook.apply(lpparam, cfg)

        // ===== [Task24] 系统级增�?=====
        if (cfg.healthDatabaseInjectEnabled) HealthDatabaseInjectHook.apply(lpparam, cfg)

        // ===== Shizuku 持久化注�?=====
        if (cfg.persistStepInjectEnabled) PersistStepInjectHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            LogX.e("模块崩溃防护: ${lpparam.packageName}", e)
            try { LogStore.add("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            AntiDetectionHelper.sleepDuringVerify()
        }
    }

    /** 目标APP包名白名�?*/
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.eg.android.AlipayGphone",     // 支付�?
        "com.tencent.mm",                   // 微信
        "com.tencent.mobileqq",             // QQ
        "com.tencent.tim",                  // TIM
        "com.xiaomi.hm.health",             // 小米运动健康
        "com.huawei.health",                // 华为运动健康
        "com.codoon.gps",                   // 咕咚
        "com.joyrun.gps",                   // 悦跑�?
        "com.keepfitness",                  // Keep
        "com.ss.android.ugc.aweme",         // 抖音
        "com.smile.gifmaker",               // 快手
        "com.netease.cloudmusic",           // 网易云音�?
        "com.tencent.wmusic",               // QQ音乐
        "com.taobao.taobao",                // 淘宝
        "com.jingdong.app.mall"             // 京东
    )

    private fun loadConfig(): StepConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { StepConfig(packageName = "global") }
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
