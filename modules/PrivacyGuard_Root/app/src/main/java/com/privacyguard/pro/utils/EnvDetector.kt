package com.privacyguard.pro.utils

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage

object EnvDetector {
    var isLocalMode: Boolean = false
        private set

    fun detect(lpparam: XC_LoadPackage.LoadPackageParam) {
        isLocalMode = try {
            lpparam.classLoader.loadClass("org.lsposed.lspatch.LSPatch")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getStoragePath(context: Context): String {
        return context.filesDir.absolutePath
    }
}
