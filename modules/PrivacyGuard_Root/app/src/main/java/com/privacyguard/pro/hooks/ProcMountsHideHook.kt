package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * /proc/mounts 隐藏Hook（Root 专属?? *
 * 功能?? *  - 通过 Shizuku mount --bind 将过滤后??mounts 挂载??/proc/mounts
 *  - 隐藏 Magisk 相关的挂载点信息，防??APP 通过 /proc/mounts 检??Magisk
 *
 * 应用场景?? *  - 部分 APP 通过读取 /proc/mounts 检??Magisk 挂载点（??magisk, modules 等）
 *  - 生成干净??mounts 文件，过滤掉 Magisk 相关?? */
object ProcMountsHideHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.procMountsHideEnabled) return
        LogX.i("ProcMountsHideHook 启动（Root 专属??)

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 /proc/mounts 隐藏")
            return
        }

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        ShizukuHelper.execShellSilent("cp /proc/mounts /data/local/tmp/mounts.bak")
                        ShizukuHelper.execShellSilent("grep -v -E 'magisk|modules|sbin' /proc/mounts > /data/local/tmp/clean_mounts")
                        ShizukuHelper.execShellSilent("mount --bind /data/local/tmp/clean_mounts /proc/mounts")
                        LogX.i("Shizuku /proc/mounts 挂载隐藏完成")
                    } catch (e: Throwable) {
                        LogX.w("Shizuku /proc/mounts 隐藏异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->ProcMountsHide")
    }
}
