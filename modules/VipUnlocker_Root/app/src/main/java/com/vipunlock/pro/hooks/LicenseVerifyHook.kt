package com.vipunlock.pro.hooks

import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Google Play License 授权 Hook（Root 专属�?
 *
 * 目标：让 APP 通过 Google Play License 校验，返回已授权�?
 *
 * 候�?Hook 类：
 *  1. com.google.android.vending.licensing.LicenseChecker
 *  2. com.google.android.vending.licensing.LicenseCheckerResult
 *  3. com.google.android.vending.licensing.Policy
 *  4. com.google.android.vending.licensing.AESObfuscator
 *
 * 硬性限制：
 *  - �?Hook 应用进程�?License 校验回调
 *  - 国内 APP 多不�?Google License，本 Hook 主要影响 Google Play 付费应用
 *  - 服务�?License 校验不绕�?
 */
object LicenseVerifyHook {

    private val LICENSE_CLASS_CANDIDATES = listOf(
        "com.google.android.vending.licensing.LicenseChecker",
        "com.google.android.vending.licensing.LicenseCheckerResult",
        "com.google.android.vending.licensing.Policy",
        "com.google.android.vending.licensing.StrictPolicy",
        "com.google.android.vending.licensing.ServerManagedPolicy",
        "com.google.android.vending.licensing.AESObfuscator",
        "com.google.android.vending.licensing.ValidationException"
    )

    /** License 校验结果方法�?*/
    private val LICENSE_METHODS = listOf(
        "allowAccess", "isLicensed", "isAuthorized", "isPurchased",
        "verifyLicense", "checkLicense", "onLicenseResponse"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.licenseVerifyEnabled) return
        LogX.i("Google License 授权 Hook 启动（Root 专属�?)

        hookLicenseChecker(lpparam)
        hookPolicy(lpparam)
        hookLicenseResultCallback(lpparam)
    }

    /** Hook LicenseChecker 的核心校验方�?*/
    private fun hookLicenseChecker(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in LICENSE_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in LICENSE_METHODS) {
                tryHookBooleanReturning(cls, clsName, m, true)
            }
        }
    }

    /** Hook Policy.allowAccess 返回 true */
    private fun hookPolicy(lpparam: XC_LoadPackage.LoadPackageParam) {
        val policyCandidates = listOf(
            "com.google.android.vending.licensing.Policy",
            "com.google.android.vending.licensing.StrictPolicy",
            "com.google.android.vending.licensing.ServerManagedPolicy"
        )
        for (clsName in policyCandidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            // allowAccess(int response) -> true
            try {
                XposedHelpers.findAndHookMethod(cls, "allowAccess",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = true
                        }
                    })
                LogX.hookSuccess(clsName, "allowAccess(int)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // 无参 allowAccess() -> true
            tryHookBooleanReturning(cls, clsName, "allowAccess", true)

            // processServerResponse -> 不修改返回值但日志
            try {
                XposedHelpers.findAndHookMethod(cls, "processServerResponse",
                    Int::class.javaPrimitiveType, "com.google.android.vending.licensing.ResponseData",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("$clsName.processServerResponse 已调�?)
                        }
                    })
                LogX.hookSuccess(clsName, "processServerResponse")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    /** Hook LicenseCheckerCallback.onAllow / donAllow 强制�?onAllow */
    private fun hookLicenseResultCallback(lpparam: XC_LoadPackage.LoadPackageParam) {
        // LicenseCheckerCallback �?APP 自己实现的接口，类名不定，用反射�?
        val callbackCandidates = listOf(
            "com.google.android.vending.licensing.LicenseCheckerCallback",
            "com.google.android.vending.licensing.LicenseChecker\$LicenseCheckerCallbackImpl"
        )
        for (clsName in callbackCandidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue

            // onAllow() -> 不修改，允许通过
            try {
                XposedHelpers.findAndHookMethod(cls, "onAllow",
                    "android.app.Activity",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("LicenseCheckerCallback.onAllow 已触�?)
                        }
                    })
                LogX.hookSuccess(clsName, "onAllow")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // onAllow() 无参
            tryHookNoOp(cls, clsName, "onAllow")

            // donAllow() 拦截，改为允�?
            try {
                XposedHelpers.findAndHookMethod(cls, "dontAllow",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("LicenseCheckerCallback.dontAllow 被拦截，强制改为允许")
                            p.result = null
                        }
                    })
                LogX.hookSuccess(clsName, "dontAllow(int)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // applicationError -> 拦截
            try {
                XposedHelpers.findAndHookMethod(cls, "applicationError",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("LicenseCheckerCallback.applicationError 被拦�?)
                            p.result = null
                        }
                    })
                LogX.hookSuccess(clsName, "applicationError(int)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun tryHookBooleanReturning(cls: Class<*>, clsName: String, method: String, value: Boolean): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) { p.result = value }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }

    private fun tryHookNoOp(cls: Class<*>, clsName: String, method: String): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = null
                }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }
}
