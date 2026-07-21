package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * zram优化(禁用swap/zram reset)（Root 专属�?
 *
 * 通过 Shizuku 执行系统级操作�?
 * 硬性限制：需 Shizuku root 级授�?
 */
object ZramOptimizerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.zramOptimizerEnabled) return
        LogX.i("ZramOptimizerHook 启动（Root 专属�?)

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过ZramOptimizerHook")
                            return
                        }
                        execute()
                        LogX.i("ZramOptimizerHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("ZramOptimizerHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->ZramOptimizerHook")
    }

    private fun execute() {
        // 禁用 zram 节省内存管理开销
        ShizukuHelper.execShellSilent("echo 1 > /sys/block/zram0/reset")
        ShizukuHelper.execShellSilent("swapoff -a")
        LogX.d("zram 已重置，swap 已关�?)
    }
}
