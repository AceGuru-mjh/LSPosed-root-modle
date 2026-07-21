package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogStore
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 剪贴板保护Hook（应用层�?
 *
 * 功能�?
 *  1. 记录 APP 读取剪贴板行为（防偷读）
 *  2. 可选阻�?getPrimaryClip 返回（防 APP 偷读剪贴板内容）
 */
object ClipboardGuardHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.clipboardGuardEnabled) return
        LogX.i("剪贴板保护启动（应用层）：阻�?${cfg.clipboardBlockRead}")

        hookClipboardManager(lpparam, cfg.clipboardBlockRead)
    }

    private fun hookClipboardManager(lpparam: XC_LoadPackage.LoadPackageParam, blockRead: Boolean) {
        try {
            val cm = XposedHelpers.findClassIfExists(
                "android.content.ClipboardManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cm, "getPrimaryClip", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.w("检测到APP读取剪贴�? ${p.thisObject?.javaClass?.name}")
                        try { LogStore.add("blocked", "阻止剪贴板读�?) } catch (_: Exception) { }
                        try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                        if (blockRead) p.result = null
                    }
                })
                LogX.hookSuccess("ClipboardManager", "getPrimaryClip")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cm, "getPrimaryClipDescription", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("APP读取剪贴板描�?)
                        if (blockRead) p.result = null
                    }
                })
                LogX.hookSuccess("ClipboardManager", "getPrimaryClipDescription")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cm, "hasPrimaryClip", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (blockRead) p.result = false
                    }
                })
                LogX.hookSuccess("ClipboardManager", "hasPrimaryClip")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cm, "setPrimaryClip",
                    "android.content.ClipData", object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("APP写入剪贴板（已记录，未阻断）")
                        }
                    })
                LogX.hookSuccess("ClipboardManager", "setPrimaryClip")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("ClipboardManager", "primary-clip", e)
        }
    }
}
