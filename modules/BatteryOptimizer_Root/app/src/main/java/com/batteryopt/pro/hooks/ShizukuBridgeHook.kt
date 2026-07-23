package com.batteryopt.pro.hooks

import android.content.Context
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 桥接 Hook
 *
 * 功能??
 *  - 统一??Shizuku 调用入口，供其他系统??Hook 复用
 *  - 启动时检??Shizuku 可用性并打日??
 *  - 提供 Shell 命令执行/系统属性修改的便捷封装
 *
 * 注意??
 *  - 不直??Hook 业务方法，仅作为 Shizuku 调用??
 *  - 所有系统级 Hook 内部已直接调??ShizukuHelper，本类主要用??
 *    启动时检测和集中日志输出
 *
 * §4.2 命令执行??Hook：通过 Hook Application.onCreate 触发诊断
 * （检??Shizuku 可用??+ 输出系统信息），使本类不再为空壳??
 */
object ShizukuBridgeHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (isApplied) return

        LogX.i("Shizuku 桥接启动")

        // §4.2 命令执行??Hook：Hook Application.onCreate 触发 Shizuku 诊断
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val ctx = p.thisObject as? Context ?: return
                    isApplied = true
                    checkShizukuAvailability()
                    logSystemInfo(ctx)
                }
            })
        LogX.hookSuccess("Application", "onCreate->ShizukuBridge")
    }

    private fun checkShizukuAvailability() {
        val available = ShizukuHelper.isShizukuAvailable()
        if (available) {
            LogX.i("Shizuku 已就绪，系统??Hook 将可??)
        } else {
            LogX.w("Shizuku 不可用，系统??Hook（Doze/冻结/CPU/Greenify）将失效")
            LogX.w("请在 Shizuku App 中激活服务并授权本模??)
        }
    }

    private fun logSystemInfo(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        if (!ShizukuHelper.isShizukuAvailable()) return
        try {
            val buildId = ShizukuHelper.execShell("getprop ro.build.id")?.trim()
            val sdk = ShizukuHelper.execShell("getprop ro.build.version.sdk")?.trim()
            val deviceIdleSupport = ShizukuHelper.execShell(
                "command -v dumpsys && dumpsys deviceidle help 2>&1 | head -1"
            )?.trim()
            LogX.i("系统信息: buildId=$buildId sdk=$sdk deviceIdle=$deviceIdleSupport")
        } catch (e: Exception) {
            LogX.e("获取系统信息异常", e)
        }
    }

    fun release() {
        ShizukuHelper.release()
        isApplied = false
        LogX.d("Shizuku 桥接资源已释??)
    }
}
