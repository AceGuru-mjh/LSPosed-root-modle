package com.batteryopt.pro.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * CPU 调度策略 Hook（需 Shizuku/Root�?
 *
 * 功能�?
 *  - 通过 Shizuku 读写 /sys/devices/system/cpu/cpuN/cpufreq/scaling_governor
 *  - 屏幕关闭时切换为 powersave governor，降�?CPU 频率省电
 *  - 屏幕亮起恢复 interactive / schedutil，恢复性能
 *
 * §4.2 命令执行�?Hook：通过 Hook Application.onCreate 触发广播注册�?
 * 由屏幕开关广播驱�?`echo $governor > scaling_governor` 命令执行�?
 */
object CpuGovernorHook {

    private var screenReceiver: BroadcastReceiver? = null

    private val cpuIndices = 0..7

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.cpuGovernorEnabled) {
            LogX.d("CPU 调度策略未开启，跳过")
            return
        }

        LogX.i("CPU 调度策略启动 | active=${cfg.cpuGovernorActive} idle=${cfg.cpuGovernorIdle}")

        // §4.2 命令执行�?Hook：Hook Application.onCreate 触发屏幕广播注册
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val ctx = p.thisObject as? Context ?: return
                    if (!ShizukuHelper.isShizukuAvailable()) {
                        LogX.w("Shizuku 不可用，跳过 CPU governor 广播注册")
                        return
                    }
                    registerScreenReceiver(ctx, cfg)
                }
            })
        LogX.hookSuccess("Application", "onCreate->CpuGovernor")
    }

    private fun registerScreenReceiver(ctx: Context, cfg: BatteryConfig) {
        try {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> setGovernor(cfg.cpuGovernorIdle)
                        Intent.ACTION_SCREEN_ON -> setGovernor(cfg.cpuGovernorActive)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ctx.registerReceiver(screenReceiver, filter)
            LogX.i("CPU 屏幕开关广播已注册")
        } catch (e: Exception) {
            LogX.e("注册屏幕广播异常", e)
        }
    }

    private fun setGovernor(governor: String) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 governor 设置")
            return
        }
        var successCount = 0
        var failCount = 0
        for (i in cpuIndices) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor"
            val cmd = "echo $governor > $path"
            ShizukuHelper.execShell(cmd)
            val verify = ShizukuHelper.execShell("cat $path 2>/dev/null")?.trim()
            if (verify == governor) {
                successCount++
            } else {
                failCount++
            }
        }
        LogX.i("Governor=$governor | 成功=$successCount 失败=$failCount")
    }
}
