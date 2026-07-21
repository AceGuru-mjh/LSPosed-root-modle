package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogStore
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 温控 / 降频屏蔽 Hook（系统级，需 Shizuku/Root�?
 *
 * 功能�?
 *  - Hook HardwarePropertiesManager.getDeviceTemperatures 压制温度�?
 *  - Hook IThermalService.getCurrentTemperatures 压制温度�?
 *  - Hook PowerManager.getCurrentThermalStatus / addThermalStatusListener
 *  - Hook 高�?MTK 厂商 CPU/GPU 调频服务，屏�?setMaxFreq/setMaxGpuFreq/throttle
 *  - Hook 厂商温控服务（MiuiThermalService/OriginOS ThermalService 等）
 *
 * 注意：本 Hook 仅在游戏进程�?Hook，不影响日常系统温控�?
 * 高温�?SOC 硬件级保护仍会触发（�?80-90℃），这是正常安全机制�?
 */
object ThermalBypassHook {

    private var thermalThreshold = 50  // 默认 50°C 才触�?

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.thermalBypassEnabled) return
        thermalThreshold = cfg.customThermalThreshold
        LogX.i("温控屏蔽启动: 阈�?${thermalThreshold}°C（系统级 Hook�?)
        try { LogStore.add("unlocked", "温控屏蔽已启�?) } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookThermalService(lpparam)
        hookCPUFreqGovernor(lpparam)
        hookGPUFreqGovernor(lpparam)
        hookPowerManager(lpparam)
        hookSystemThermalNodes(lpparam)
    }

    /** Hook HardwarePropertiesManager + IThermalService 温度读取 */
    private fun hookThermalService(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val hpm = XposedHelpers.findClassIfExists(
                "android.os.HardwarePropertiesManager", lpparam.classLoader)
            if (hpm != null) {
                try {
                    XposedHelpers.findAndHookMethod(hpm, "getDeviceTemperatures",
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                val result = p.result as? Array<*> ?: return
                                try {
                                    val tempClass = result.firstOrNull()?.javaClass ?: return
                                    val tempField = tempClass.getDeclaredField("temperature")
                                    tempField.isAccessible = true
                                    for (tempObj in result) {
                                        if (tempObj != null) {
                                            val currentTemp = tempField.getFloat(tempObj)
                                            if (currentTemp > thermalThreshold) {
                                                tempField.setFloat(tempObj, thermalThreshold.toFloat())
                                            }
                                        }
                                    }
                                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                            }
                        })
                    LogX.hookSuccess("HardwarePropertiesManager", "getDeviceTemperatures")
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }

            val thermalServiceClass = XposedHelpers.findClassIfExists(
                "android.os.IThermalService", lpparam.classLoader)
            if (thermalServiceClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(thermalServiceClass, "getCurrentTemperatures",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                val temperatures = p.result as? List<*> ?: return
                                p.result = temperatures.map { temp ->
                                    try {
                                        val tempClass = temp?.javaClass ?: return@map temp
                                        val valueField = tempClass.getDeclaredField("mValue")
                                        valueField.isAccessible = true
                                        val currentValue = valueField.getFloat(temp)
                                        if (currentValue > thermalThreshold) {
                                            valueField.setFloat(temp, thermalThreshold.toFloat())
                                        }
                                    } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                    temp
                                }
                            }
                        })
                    LogX.hookSuccess("IThermalService", "getCurrentTemperatures")
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }
        } catch (e: Throwable) {
            LogX.hookFailed("ThermalService", "getDeviceTemperatures", e)
        }
    }

    /** Hook 高�?/ MTK CPU 调频服务 */
    private fun hookCPUFreqGovernor(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cpuGovernorPaths = listOf(
            "com.android.server.CpuGovernorService",
            "android.os.CpuGovernorManager",
            "com.qualcomm.qti.Performance",
            "com.mediatek.performance.Performance"
        )
        for (path in cpuGovernorPaths) {
            try {
                val cls = XposedHelpers.findClassIfExists(path, lpparam.classLoader) ?: continue
                for (method in cls.declaredMethods) {
                    if (method.name.contains("setMaxFreq", ignoreCase = true) ||
                        method.name.contains("setScalingMax", ignoreCase = true) ||
                        method.name.contains("lockFreq", ignoreCase = true)) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                        LogX.hookSuccess(cls.name, method.name)
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** Hook 高�?/ MTK GPU 调频服务 */
    private fun hookGPUFreqGovernor(lpparam: XC_LoadPackage.LoadPackageParam) {
        val gpuGovernorPaths = listOf(
            "com.qualcomm.qti.GPUPerformance",
            "android.os.GPUGovernorManager",
            "com.mediatek.gpu.GPUManager"
        )
        for (path in gpuGovernorPaths) {
            try {
                val cls = XposedHelpers.findClassIfExists(path, lpparam.classLoader) ?: continue
                for (method in cls.declaredMethods) {
                    if (method.name.contains("setMaxGpuFreq", ignoreCase = true) ||
                        method.name.contains("setGPUClock", ignoreCase = true) ||
                        method.name.contains("lockGpuClock", ignoreCase = true) ||
                        method.name.contains("throttleGpu", ignoreCase = true)) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                        LogX.hookSuccess(cls.name, method.name)
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    /** Hook PowerManager 温控回调 */
    private fun hookPowerManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.os.PowerManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(pm, "getCurrentThermalStatus",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = 0  // STATUS_NONE
                        }
                    })
                LogX.hookSuccess("PowerManager", "getCurrentThermalStatus -> STATUS_NONE")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(pm, "addThermalStatusListener",
                    "android.os.PowerManager.OnThermalStatusChangedListener",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = false
                        }
                    })
                LogX.hookSuccess("PowerManager", "addThermalStatusListener(blocked)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("PowerManager", "thermal", e)
        }
    }

    /** Hook 厂商温控服务（MiuiThermal/OriginOS Thermal/ColorOS Thermal 等） */
    private fun hookSystemThermalNodes(lpparam: XC_LoadPackage.LoadPackageParam) {
        val targets = listOf(
            "com.miui.powerkeeper.thermal.MiuiThermalService",
            "com.vivo.thermal.ThermalService",
            "com.oplus.thermal.ThermalAdapter",
            "com.samsung.android.thermal.ThermalManagerService",
            "com.hihonor.thermal.HonorThermalService",
            "com.qualcomm.qti.thermal.ThermalService",
            "com.mediatek.thermal.manager.ThermalManager"
        )
        for (cls in targets) {
            try {
                val c = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
                for (method in c.declaredMethods) {
                    if (method.name.contains("thermal", ignoreCase = true) ||
                        method.name.contains("throttle", ignoreCase = true) ||
                        method.name.contains("limit", ignoreCase = true)) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                        LogX.hookSuccess(cls, method.name)
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }
}
