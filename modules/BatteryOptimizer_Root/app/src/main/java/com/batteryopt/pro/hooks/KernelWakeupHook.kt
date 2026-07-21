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
 * 内核唤醒优化 Hook（系统级，需 Shizuku/Root 授权�?
 *
 * 功能（在 Application.onCreate 与电池状态广播中通过 Shizuku 执行）：
 *  1. 充电时屏幕常亮：settings put global stay_on_while_plugged_in 3
 *     �? = AC + USB 都常亮，部分用户希望插电开发时不锁屏）
 *  2. 屏幕亮起时持�?wake_lock（echo xxx > /sys/power/wake_lock�?
 *     屏幕关闭时释放（echo xxx > /sys/power/wake_unlock�?
 *  3. 监听 ACTION_POWER_CONNECTED / DISCONNECTED 自动切换 stay_on
 *
 * §4.2 命令执行�?Hook：Hook Application.onCreate 触发 Shizuku 命令执行，避免空壳�?
 */
object KernelWakeupHook {

    /** 自定�?wake_lock 标识（避免与系统其他 wake_lock 冲突�?*/
    private const val WAKE_LOCK_TAG = "lsp_battery_opt_wakelock"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.kernelWakeupEnabled) return
        LogX.i("内核唤醒优化启动（系统级，需 Shizuku�?)

        // §4.2 命令执行�?Hook：Hook Application.onCreate 触发 Shizuku 命令
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val ctx = p.thisObject as? Context
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过内核唤醒优化")
                            return
                        }
                        if (cfg.stayOnWhilePluggedIn) applyStayOnWhilePluggedIn()
                        ctx?.let { registerReceivers(it, cfg) }
                    } catch (e: Throwable) {
                        LogX.w("内核唤醒优化初始化异�? ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->KernelWakeup")
    }

    /** 充电时屏幕常亮（settings put global stay_on_while_plugged_in 3�?*/
    private fun applyStayOnWhilePluggedIn() {
        try {
            val ok = ShizukuHelper.execShellSilent("settings put global stay_on_while_plugged_in 3")
            if (ok) LogX.i("已开启充电时屏幕常亮 (stay_on_while_plugged_in=3)")
            else LogX.w("stay_on_while_plugged_in 设置失败")
        } catch (e: Throwable) {
            LogX.w("applyStayOnWhilePluggedIn 异常: ${e.message}")
        }
    }

    /** 关闭充电时屏幕常亮（恢复默认 0�?*/
    private fun disableStayOnWhilePluggedIn() {
        try {
            ShizukuHelper.execShellSilent("settings put global stay_on_while_plugged_in 0")
            LogX.i("已关闭充电时屏幕常亮")
        } catch (e: Throwable) {
            LogX.w("disableStayOnWhilePluggedIn 异常: ${e.message}")
        }
    }

    /** 注册电源/屏幕广播 */
    private fun registerReceivers(ctx: Context, cfg: BatteryConfig) {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    try {
                        when (intent?.action) {
                            Intent.ACTION_POWER_CONNECTED -> {
                                if (!ShizukuHelper.isShizukuAvailable()) return
                                if (cfg.stayOnWhilePluggedIn) applyStayOnWhilePluggedIn()
                            }
                            Intent.ACTION_POWER_DISCONNECTED -> {
                                if (!ShizukuHelper.isShizukuAvailable()) return
                                if (cfg.stayOnWhilePluggedIn) disableStayOnWhilePluggedIn()
                            }
                            Intent.ACTION_SCREEN_ON -> {
                                if (!ShizukuHelper.isShizukuAvailable()) return
                                acquireWakeLock()
                            }
                            Intent.ACTION_SCREEN_OFF -> {
                                if (!ShizukuHelper.isShizukuAvailable()) return
                                releaseWakeLock()
                            }
                        }
                    } catch (e: Throwable) {
                        LogX.w("KernelWakeup 广播异常: ${e.message}")
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ctx.registerReceiver(receiver, filter)
            LogX.i("KernelWakeup 广播已注�?)
        } catch (e: Throwable) {
            LogX.w("registerReceivers 异常: ${e.message}")
        }
    }

    /** 持有 wake_lock（echo tag > /sys/power/wake_lock�?*/
    private fun acquireWakeLock() {
        try {
            val ok = ShizukuHelper.execShellSilent("echo $WAKE_LOCK_TAG > /sys/power/wake_lock")
            if (ok) LogX.d("已持�?wake_lock: $WAKE_LOCK_TAG")
            else LogX.w("wake_lock 获取失败")
        } catch (e: Throwable) {
            LogX.w("acquireWakeLock 异常: ${e.message}")
        }
    }

    /** 释放 wake_lock（echo tag > /sys/power/wake_unlock�?*/
    private fun releaseWakeLock() {
        try {
            ShizukuHelper.execShellSilent("echo $WAKE_LOCK_TAG > /sys/power/wake_unlock")
            LogX.d("已释�?wake_lock: $WAKE_LOCK_TAG")
        } catch (e: Throwable) {
            LogX.w("releaseWakeLock 异常: ${e.message}")
        }
    }
}
