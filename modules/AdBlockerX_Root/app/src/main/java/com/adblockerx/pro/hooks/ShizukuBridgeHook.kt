package com.adblockerx.pro.hooks

import android.content.Context
import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 桥接 Hook（Root 版独有）
 *
 * 功能�?
 *  - 通过 Shizuku 执行系统命令刷新 DNS 缓存
 *    - ndc resolver flushdefaultif（Android N+�?
 *    - settings put global dns_cache_seconds 0
 *  - 联动 SystemHostsHook / PrivateDnsHook 确保修改立即生效
 *
 * §4.2 命令执行�?Hook：通过 Hook Application.onCreate 触发 Shizuku 命令执行
 * （刷�?DNS 缓存），避免空壳�?
 */
object ShizukuBridgeHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.shizukuBridgeEnabled) {
            LogX.d("ShizukuBridge 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("ShizukuBridge 启动：刷新系�?DNS 缓存")

        // §4.2 命令执行�?Hook：Hook Application.onCreate 触发 DNS 缓存刷新
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val ctx = p.thisObject as? Context ?: return
                    isApplied = true
                    if (!ShizukuHelper.isShizukuAvailable()) {
                        LogX.w("Shizuku 不可用，跳过 DNS 刷新")
                        return
                    }
                    flushDnsCache(ctx)
                }
            })
        LogX.hookSuccess("Application", "onCreate->ShizukuBridge")
    }

    /** �?Application.onCreate 后刷新系�?DNS 缓存 */
    private fun flushDnsCache(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        val r1 = ShizukuHelper.execShell("ndc resolver flushdefaultif 2>&1")
        LogX.d("ndc resolver flushdefaultif -> $r1")

        val r2 = ShizukuHelper.execShell("ndc resolver flushnetid 2>&1")
        LogX.d("ndc resolver flushnetid -> $r2")

        ShizukuHelper.execShell("settings put global dns_cache_seconds 0 2>/dev/null")
        ShizukuHelper.execShell("killall -HUP dnsmasq 2>/dev/null")

        LogX.i("DNS 缓存刷新完成")
    }

    fun release() {
        isApplied = false
    }
}
