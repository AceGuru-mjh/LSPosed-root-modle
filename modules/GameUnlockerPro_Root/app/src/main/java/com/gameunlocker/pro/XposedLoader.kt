package com.gameunlocker.pro

import android.app.Application
import android.util.Log
import com.gameunlocker.pro.models.GameConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-GameUnlocker-Root"
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
        Log.i(TAG, "GameUnlocker Pro v$VERSION 初始化 | LSPatch/LSPosed 兼容 | 系统级 Hook 已就绪")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetGame(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== GameUnlockerPro v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
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
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} 伪装=${cfg.deviceSpoofEnabled} " +
                "帧率=${cfg.targetFps}fps 隐藏=${cfg.detectionHideEnabled} " +
                "温控=${cfg.thermalBypassEnabled} GPU=${cfg.gpuOptimizeEnabled} " +
                "Shizuku=${cfg.shizukuBridgeEnabled} " +
                "[实验]触摸=${cfg.touchSamplingBoostEnabled} 网络=${cfg.networkLatencyOptEnabled} " +
                "音频=${cfg.audioPriorityBoostEnabled} 内存=${cfg.memoryDefragEnabled} " +
                "游戏模式=${cfg.gameModeActivationEnabled} CPU亲和=${cfg.cpuBigCoreAffinityEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有 Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.gameunlocker.pro.hooks."

        if (cfg.detectionHideEnabled) tryInvoke(HP + "GameDetectionHideHook", "apply", loader, lpparam, cfg)

        if (cfg.deviceSpoofEnabled) tryInvoke(HP + "DeviceSpoofHook", "apply", loader, lpparam, cfg)

        if (cfg.frameRateUnlockEnabled) tryInvoke(HP + "FrameRateUnlockHook", "apply", loader, lpparam, cfg)

        if (cfg.thermalBypassEnabled) tryInvoke(HP + "ThermalBypassHook", "apply", loader, lpparam, cfg)

        if (cfg.gpuOptimizeEnabled) tryInvoke(HP + "GPUSchedulerHook", "apply", loader, lpparam, cfg)

        if (cfg.processOptimizeEnabled) tryInvoke(HP + "ProcessOptimizerHook", "apply", loader, lpparam, cfg)

        if (cfg.resolutionSpoofEnabled) tryInvoke(HP + "ResolutionSpoofHook", "apply", loader, lpparam, cfg)

        if (cfg.shizukuBridgeEnabled) tryInvoke(HP + "ShizukuBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.touchSamplingBoostEnabled) tryInvoke(HP + "TouchSamplingBoostHook", "apply", loader, lpparam, cfg)
        if (cfg.networkLatencyOptEnabled) tryInvoke(HP + "NetworkLatencyOptHook", "apply", loader, lpparam, cfg)
        if (cfg.audioPriorityBoostEnabled) tryInvoke(HP + "AudioPriorityBoostHook", "apply", loader, lpparam, cfg)
        if (cfg.memoryDefragEnabled) tryInvoke(HP + "MemoryDefragHook", "apply", loader, lpparam, cfg)

        if (cfg.gameModeActivationEnabled) tryInvoke(HP + "GameModeActivationHook", "apply", loader, lpparam, cfg)
        if (cfg.cpuBigCoreAffinityEnabled) tryInvoke(HP + "CpuBigCoreAffinityHook", "apply", loader, lpparam, cfg)

        if (cfg.kernelTunerEnabled) tryInvoke(HP + "KernelTunerHook", "apply", loader, lpparam, cfg)
        if (cfg.gpuFreqLockEnabled) tryInvoke(HP + "GpuFreqLockHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部 Hook 就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.gameunlocker.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetGame(pkg: String) = pkg in listOf(
        "com.tencent.tmgp.sgame",
        "com.miHoYo.Yuanshen",
        "com.miHoYo.GenshinImpact",
        "com.tencent.tmgp.pubgmhd",
        "com.tencent.ig",
        "com.miHoYo.hkrpg",
        "com.tencent.tmgp.cod",
        "com.activision.callofduty.shooter",
        "com.tencent.tmgp.gnyx",
        "com.gameblackmyth.mobile",
        "com.miHoYo.ZenlessZoneZero",
        "com.kurogame.kjq"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.gameunlocker.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.gameunlocker.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.gameunlocker.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): GameConfig {
        try {
            Class.forName("com.gameunlocker.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as GameConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.gameunlocker.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? GameConfig ?: GameConfig(packageName = "global")
        } catch (_: Throwable) { GameConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.gameunlocker.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.gameunlocker.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.gameunlocker.pro.utils.LogStore")
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
                            Class.forName("com.gameunlocker.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.gameunlocker.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
