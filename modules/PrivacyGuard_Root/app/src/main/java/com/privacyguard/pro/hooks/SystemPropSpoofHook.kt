package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.FakeDeviceCache
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统属性伪造Hook（Root 专属，需 Shizuku adb 级授权）
 *
 * 功能�?
 *  - 应用�?Hook SystemProperties.get 保持伪造一致�?
 *  - 通过 Shizuku setprop 修改 ro.serialno / ro.boot.serialno / ro.product.* 等属�?
 *
 * 硬性限制：
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - ro.* 属性原生不可写，setprop 对部分只读属性无�?
 *  - 持久化需写入 build.prop（需�?root �?Shizuku），重启后消�?
 */
object SystemPropSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.systemPropSpoofEnabled) return
        LogX.i("系统属性伪造启动（Root 专属�?)

        hookSystemProperties(lpparam, cfg)
        applySystemPropsViaShizuku(cfg)
    }

    private fun hookSystemProperties(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val sp = XposedHelpers.findClassIfExists(
                "android.os.SystemProperties", lpparam.classLoader) ?: return

            val props = mutableMapOf(
                "ro.serialno" to FakeDeviceCache.fakeSerial,
                "ro.boot.serialno" to FakeDeviceCache.fakeSerial,
                "ro.product.model" to cfg.spoofProductModel,
                "ro.product.brand" to cfg.spoofProductBrand,
                "ro.product.manufacturer" to cfg.spoofProductManufacturer,
                "ro.product.name" to cfg.spoofProductModel.replace(" ", "_"),
                "ro.product.device" to cfg.spoofProductModel.replace(" ", "_").lowercase()
            )

            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as String
                            if (props.containsKey(key)) {
                                p.result = props[key]
                            }
                        }
                    })
                LogX.hookSuccess("SystemProperties", "get(key, def)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as String
                            if (props.containsKey(key)) {
                                p.result = props[key]
                            }
                        }
                    })
                LogX.hookSuccess("SystemProperties", "get(key)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            LogX.i("SystemProperties Hook完成: ${props.size}个属�?)
        } catch (e: Exception) {
            LogX.hookFailed("SystemProperties", "get", e)
        }
    }

    private fun applySystemPropsViaShizuku(cfg: PrivacyConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 setprop 系统属性修改（仅应用层 Hook 生效�?)
            return
        }

        val props = listOf(
            "ro.serialno" to FakeDeviceCache.fakeSerial,
            "ro.boot.serialno" to FakeDeviceCache.fakeSerial,
            "ro.product.model" to cfg.spoofProductModel,
            "ro.product.brand" to cfg.spoofProductBrand,
            "ro.product.manufacturer" to cfg.spoofProductManufacturer
        )

        var success = 0
        for ((key, value) in props) {
            if (ShizukuHelper.setSystemProperty(key, value)) {
                success++
            }
        }

        LogX.i("Shizuku setprop 完成: $success/${props.size} 个属性设置成�?)
    }
}
