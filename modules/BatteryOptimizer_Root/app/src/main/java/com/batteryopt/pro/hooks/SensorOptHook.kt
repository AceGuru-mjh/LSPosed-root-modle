package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * SensorManager 浼犳劅鍣ㄩ檷棰?Hook锛堝簲鐢ㄥ眰锛?
 */
object SensorOptHook {

    private const val HIGH_FREQ_THRESHOLD_US = 20_000

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Sensor 浼犳劅鍣ㄤ紭鍖栧惎鍔?| 涓婇檺=${cfg.sensorMaxRateUs}us")

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
                            LogX.w("浼犳劅鍣ㄩ檷棰? ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(3鍙?")
        } catch (e: Exception) {
            LogX.e("Hook registerListener(3鍙? 寮傚父", e)
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
                            LogX.w("浼犳劅鍣ㄩ檷棰?甯﹀欢杩?: ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(4鍙?")
        } catch (e: Exception) { LogX.w("寮傚父: ${e.message}") }

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
                            LogX.w("浼犳劅鍣ㄩ檷棰?甯andler): ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(甯andler)")
        } catch (e: Exception) { LogX.w("寮傚父: ${e.message}") }
    }
}
