package com.batteryopt.pro.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogStore
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统 Doze 强制 Hook（需 Shizuku/Root�?
 *
 * 功能�?
 *  - 通过 Shizuku 执行 `dumpsys deviceidle force-idle deep` 强制进入深度 Doze
 *  - 通过 `settings put global device_idle_constants ...` 调整 Doze 参数
 *  - 仅在屏幕关闭时触发，屏幕亮起时恢复（dumpsys deviceidle unforce�?
 *
 * §4.2 命令执行�?Hook：通过 Hook Application.onCreate 触发 Shizuku 命令执行
 * 与广播注册，避免空壳（hookCalls >= 1）�?
 */
object SystemDozeHook {

    private val handler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var pendingForceIdle: Runnable? = null

    private val dozeParams = mapOf(
        "inactive_after" to "30s",
        "sensing_after" to "60s",
        "locating_after" to "120s",
        "idle_after" to "300s",
        "idle_pending_timeout" to "60s",
        "max_temp_app_idle_delay_ms" to "300000"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.dozeEnabled) {
            LogX.d("系统 Doze 强制未开启，跳过")
            return
        }

        LogX.i("系统 Doze 强制启动 | 延迟=${cfg.dozeDelaySec}s")

        // §4.2 命令执行�?Hook：Hook Application.onCreate 触发 Shizuku 命令执行
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    // �?Application.onCreate 后执行：先应�?Doze 参数，再注册屏幕开关广�?
                    val ctx = p.thisObject as? Context ?: return
                    if (!ShizukuHelper.isShizukuAvailable()) {
                        LogX.w("Shizuku 不可用，跳过 Doze 命令执行")
                        return
                    }
                    applyDozeParams()
                    registerScreenReceiver(ctx, cfg)
                }
            })
        LogX.hookSuccess("Application", "onCreate->Doze")
    }

    private fun applyDozeParams() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 Doze 参数应用")
            return
        }
        for ((key, value) in dozeParams) {
            ShizukuHelper.execShell(
                "settings put global device_idle_constants $key $value"
            )
        }
        LogX.i("已应�?Doze 参数: ${dozeParams.size} �?)
    }

    private fun registerScreenReceiver(ctx: Context, cfg: BatteryConfig) {
        try {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> onScreenOff(cfg.dozeDelaySec)
                        Intent.ACTION_SCREEN_ON -> onScreenOn()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ctx.registerReceiver(screenReceiver, filter)
            LogX.i("Doze 屏幕开关广播已注册")
        } catch (e: Exception) {
            LogX.e("注册屏幕广播异常", e)
        }
    }

    private fun onScreenOff(delaySec: Int) {
        LogX.i("屏幕关闭�?{delaySec}s 后强制进入深�?Doze")
        pendingForceIdle?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (!ShizukuHelper.isShizukuAvailable()) {
                LogX.w("Shizuku 不可用，跳过 force-idle")
                return@Runnable
            }
            val out = ShizukuHelper.execShell("dumpsys deviceidle force-idle deep")
            LogX.i("已强制进入深�?Doze: $out")
            try { LogStore.add("doze", "强制进入深度Doze") } catch (_: Exception) { }
            try { LogStore.incrementCounter(1) } catch (_: Exception) { }
        }
        pendingForceIdle = r
        handler.postDelayed(r, delaySec * 1000L)
    }

    private fun onScreenOn() {
        LogX.i("屏幕亮起，恢�?Doze 自动状�?)
        pendingForceIdle?.let { handler.removeCallbacks(it) }
        pendingForceIdle = null
        if (ShizukuHelper.isShizukuAvailable()) {
            ShizukuHelper.execShell("dumpsys deviceidle unforce")
            LogX.d("已恢�?Doze 自动状�?)
        }
    }
}
