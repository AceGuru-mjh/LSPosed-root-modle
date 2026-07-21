package com.privacyguard.pro.utils

import de.robv.android.xposed.callbacks.XC_LoadPackage

object ModuleConflictDetector {
    private data class ConflictRule(val moduleName: String, val packagePatterns: List<String>)

    private val rules = listOf(
        ConflictRule("GameUnlockerPro", listOf("gameunlocker", "frame", "fps")),
        ConflictRule("PrivacyGuard", listOf("privacyguard", "privacy")),
        ConflictRule("AdBlockerX", listOf("adblocker", "adblock"))
    )

    fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        val pm = try {
            lpparam.classLoader.loadClass("android.app.ActivityThread")
                ?.getMethod("currentPackageManager")?.invoke(null)
        } catch (_: Throwable) { null }
        return false
    }
}
