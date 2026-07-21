package com.vipunlock.pro.hooks

import android.app.Application
import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.utils.LogX
import com.vipunlock.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Shizuku 权限桥接 Hook（Root 专属�?
 *
 * 目标：通过 Shizuku 执行 `pm grant <pkg> <perm>` 授予 APP 隐藏权限�?
 * 部分高级功能（如后台保活/系统级通知/存储加速）需要这些权限才开放给用户�?
 *
 * 触发方式�?
 *  - Hook Application.onCreate �?APP 启动后执�?pm grant
 *
 * 硬性限制：
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - pm grant 仅对 declared-by-system 权限有效，自定义权限无效
 *  - 部分权限授予�?APP 重启才生�?
 */
object ShizukuVipBridgeHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.shizukuVipBridgeEnabled) return
        if (isApplied) return
        isApplied = true

        LogX.i("【实验性】Shizuku 权限桥接启动（Root 专属�?)

        // 检�?Shizuku 是否可用
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，ShizukuVipBridge 仅注�?Application.onCreate 钩子�?Shizuku 启动后重�?)
        }

        hookAppLifecycleForGrant(lpparam, cfg)
    }

    /** Hook Application.onCreate �?APP 启动后执�?pm grant */
    private fun hookAppLifecycleForGrant(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        val pkg = app.packageName ?: return
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.d("Shizuku未就绪，跳过 pm grant: $pkg")
                            return
                        }
                        var success = 0
                        for (perm in cfg.grantedHiddenPermissions) {
                            if (ShizukuHelper.grantPermission(pkg, perm)) {
                                success++
                                LogX.d("已授�?$pkg 权限: $perm")
                            } else {
                                LogX.w("授予权限失败: $pkg $perm")
                            }
                        }
                        LogX.i("Shizuku pm grant 完成: $success/${cfg.grantedHiddenPermissions.size} 个权�?)
                    }
                })
            LogX.hookSuccess("Application", "onCreate(pm_grant)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate", e)
        }
    }

    fun release() {
        ShizukuHelper.release()
        isApplied = false
        LogX.d("ShizukuVipBridge 资源已释�?)
    }
}
