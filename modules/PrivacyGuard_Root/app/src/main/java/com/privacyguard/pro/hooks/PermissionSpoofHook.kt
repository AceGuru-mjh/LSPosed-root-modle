package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogStore
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 权限欺骗Hook（应用层，仅欺骗APP自身检查）
 *
 * �?GlobalPermissionHook 区别�?
 *  - 本Hook仅欺�?APP 自身权限检�?API，不真的修改系统授权
 *  - GlobalPermissionHook 真的回收权限，影响系统全局（Shizuku pm revoke�?
 */
object PermissionSpoofHook {

    private const val PERMISSION_GRANTED = 0
    private const val PERMISSION_DENIED = -1

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.permissionSpoofEnabled) return
        if (cfg.deniedPermissions.isEmpty()) {
            LogX.d("权限欺骗开启但未配置拒绝列表，跳过")
            return
        }
        LogX.i("权限欺骗启动（应用层）：拒绝 ${cfg.deniedPermissions.size} 个权�?)
        try { LogStore.add("spoofed", "伪造权限检�?) } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        val deniedSet = cfg.deniedPermissions.toSet()

        hookContextWrapperCheckPermission(lpparam, deniedSet)
        hookPackageManagerCheckPermission(lpparam, deniedSet)
        hookContextCompatCheckPermission(lpparam, deniedSet)
    }

    private fun hookContextWrapperCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val cw = XposedHelpers.findClassIfExists(
                "android.content.ContextWrapper", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cw, "checkSelfPermission",
                    String::class.java, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                                LogX.d("权限欺骗: $perm -> DENIED")
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "checkSelfPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cw, "checkPermission",
                    String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "checkPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("ContextWrapper", "checkSelfPermission", e)
        }
    }

    private fun hookPackageManagerCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(pm, "checkPermission",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("PackageManager", "checkPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("PackageManager", "checkPermission", e)
        }
    }

    private fun hookContextCompatCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val cc = XposedHelpers.findClassIfExists(
                "androidx.core.content.ContextCompat", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cc, "checkSelfPermission",
                    "android.content.Context", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[1] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("ContextCompat", "checkSelfPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (_: Exception) {
            LogX.d("ContextCompat 未找到，跳过 androidx 兼容Hook")
        }
    }
}
