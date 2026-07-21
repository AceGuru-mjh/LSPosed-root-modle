package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.FakeDeviceCache
import com.privacyguard.pro.utils.LogStore
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 设备ID伪造Hook（应用层 + Root 版）
 *
 * 拦截路径�?
 *  1. TelephonyManager.getDeviceId / getImei / getMeid / getSubscriberId / getLine1Number
 *  2. Settings.Secure.getString(ANDROID_ID)
 *  3. WifiInfo.getMacAddress / getBSSID
 *  4. Build.getSerial + SERIAL 字段
 */
object DeviceIdSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.deviceIdSpoofEnabled) return
        LogX.i("设备ID伪造启动（应用层）")
        try { LogStore.add("spoofed", "伪造设备ID") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookTelephonyManager(lpparam)
        hookSettingsSecure(lpparam)
        hookWifiInfo(lpparam)
        hookBuildSerial(lpparam)
    }

    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val tm = XposedHelpers.findClassIfExists(
                "android.telephony.TelephonyManager", lpparam.classLoader) ?: return

            listOf("getDeviceId", "getImei", "getMeid").forEach { m ->
                try {
                    XposedHelpers.findAndHookMethod(tm, m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = if (m == "getMeid") FakeDeviceCache.fakeMeid else FakeDeviceCache.fakeImei
                        }
                    })
                    LogX.hookSuccess("TelephonyManager", m)
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                try {
                    XposedHelpers.findAndHookMethod(tm, m,
                        Int::class.javaPrimitiveType, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                p.result = if (m == "getMeid") FakeDeviceCache.fakeMeid else FakeDeviceCache.fakeImei
                            }
                        })
                    LogX.hookSuccess("TelephonyManager", "$m(slot)")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }

            try {
                XposedHelpers.findAndHookMethod(tm, "getSubscriberId", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeSubscriberId
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getSubscriberId")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(tm, "getLine1Number", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeLine1Number
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getLine1Number")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(tm, "getSimSerialNumber", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeSimSerial
                    }
                })
                LogX.hookSuccess("TelephonyManager", "getSimSerialNumber")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("TelephonyManager", "device-id", e)
        }
    }

    private fun hookSettingsSecure(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ss = XposedHelpers.findClassIfExists(
                "android.provider.Settings.Secure", lpparam.classLoader) ?: return
            val ANDROID_ID = "android_id"

            try {
                XposedHelpers.findAndHookMethod(ss, "getString",
                    "android.content.ContentResolver", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val name = p.args[1] as? String ?: return
                            if (name == ANDROID_ID) p.result = FakeDeviceCache.fakeAndroidId
                        }
                    })
                LogX.hookSuccess("Settings.Secure", "getString(ANDROID_ID)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(ss, "getString",
                    "android.content.ContentResolver", String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val name = p.args[1] as? String ?: return
                            if (name == ANDROID_ID) p.result = FakeDeviceCache.fakeAndroidId
                        }
                    })
                LogX.hookSuccess("Settings.Secure", "getString(ANDROID_ID, def)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Settings.Secure", "getString", e)
        }
    }

    private fun hookWifiInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wifi = XposedHelpers.findClassIfExists(
                "android.net.wifi.WifiInfo", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wifi, "getMacAddress", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeMacAddress
                    }
                })
                LogX.hookSuccess("WifiInfo", "getMacAddress")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wifi, "getBSSID", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeBssid
                    }
                })
                LogX.hookSuccess("WifiInfo", "getBSSID")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("WifiInfo", "mac/bssid", e)
        }
    }

    private fun hookBuildSerial(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val build = XposedHelpers.findClassIfExists(
                "android.os.Build", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(build, "getSerial", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeSerial
                    }
                })
                LogX.hookSuccess("Build", "getSerial")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.setStaticObjectField(build, "SERIAL", FakeDeviceCache.fakeSerial)
                LogX.d("Build.SERIAL 字段已覆�?)
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Build", "getSerial", e)
        }
    }
}
