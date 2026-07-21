package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】存储路径混淆（应用层）
 *
 * 对外暴露的存储路径查询结果做混淆，干�?APP 通过路径进行设备识别
 */
object StoragePathSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.storagePathSpoofEnabled) return
        LogX.i("【实验性】存储路径混淆启�?)

        hookEnvironment(lpparam)
    }

    private fun hookEnvironment(lpparam: XC_LoadPackage.LoadPackageParam) {
        val env = XposedHelpers.findClassIfExists(
            "android.os.Environment", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(env, "getExternalStorageDirectory",
                object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    LogX.d("Environment.getExternalStorageDirectory 被查�?)
                }
            })
            LogX.hookSuccess("Environment", "getExternalStorageDirectory")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(env, "isExternalStorageEmulated",
                object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = false
                }
            })
            LogX.hookSuccess("Environment", "isExternalStorageEmulated")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(env, "isExternalStorageRemovable",
                object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = true
                }
            })
            LogX.hookSuccess("Environment", "isExternalStorageRemovable")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
