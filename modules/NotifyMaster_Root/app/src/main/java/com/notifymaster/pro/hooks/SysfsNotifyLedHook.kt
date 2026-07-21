package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import com.notifymaster.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Sysfs 通知 LED 控制（Root v1.1.0）
 *
 * 通过 Shizuku 写 /sys/class/leds 节点控制通知指示灯和振动模式。
 * 硬性限制：需 Shizuku root 级授权
 */
object SysfsNotifyLedHook {

    private val ledPaths = listOf(
        "/sys/class/leds/red/brightness",
        "/sys/class/leds/green/brightness",
        "/sys/class/leds/blue/brightness",
        "/sys/class/leds/white/brightness",
        "/sys/class/leds/notify/brightness",
        "/sys/class/leds/charging/brightness"
    )

    private val vibratorPaths = listOf(
        "/sys/class/timed_output/vibrator/enable",
        "/sys/class/leds/vibrator/activate",
        "/sys/devices/virtual/timed_output/vibrator/enable"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.sysfsLedEnabled) return
        LogX.i("SysfsNotifyLedHook 启动（Root v1.1.0）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过SysfsNotifyLedHook")
                            return
                        }
                        execute()
                        LogX.i("SysfsNotifyLedHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("SysfsNotifyLedHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->SysfsNotifyLedHook")
    }

    private fun execute() {
        val sh = ShizukuHelper

        // 探测可用的 LED 节点并点亮红色
        for (path in ledPaths) {
            val result = sh.execShell("echo 255 > $path 2>/dev/null")
            if (result != null) {
                LogX.d("[root] LED 节点已写入: $path -> 255")
                break
            }
        }

        // 关闭绿色 LED
        sh.execShellSilent("echo 0 > /sys/class/leds/green/brightness 2>/dev/null")
        LogX.d("[root] LED green -> 0")

        // 设置振动模式
        for (path in vibratorPaths) {
            if (sh.execShellSilent("echo 3000 > $path 2>/dev/null")) {
                LogX.d("[root] 振动器节点已写入: $path 模式=3000ms")
                break
            }
        }

        // 写入通知 LED 模式
        sh.execShellSilent("echo 1 > /sys/class/leds/notify/trigger 2>/dev/null")
        LogX.d("[root] 通知 LED trigger 模式已激活")
    }
}
