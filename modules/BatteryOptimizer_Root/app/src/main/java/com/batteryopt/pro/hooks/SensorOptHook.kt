package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * SensorManager 传感器降�?Hook（应用层�?
 */
object SensorOptHook {

    private const val HIGH_FREQ_THRESHOLD_US = 20_000

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Sensor 传感器优化启�?| 上限=${cfg.sensorMaxRateUs}us")

        hookRegisterListener(lpparam, cfg)
    }

    private fun hookRegisterListener(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        val smCls = XposedHelpers.findClassIfExists(
            "android.hardware.SensorManager", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降�? ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(3�?")
        } catch (e: Exception) {
            LogX.e("Hook registerListener(3�? 异常", e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降�?带延�?: ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(4�?")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                "android.os.Handler",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降�?带Handler): ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(带Handler)")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }
}
