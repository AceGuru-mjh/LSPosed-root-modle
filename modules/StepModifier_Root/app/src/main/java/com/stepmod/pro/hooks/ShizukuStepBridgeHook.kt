package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 步数广播桥接 Hook（Root 实验性）
 *
 * 功能�?
 *  - 通过 Shizuku 执行 am broadcast 广播步数更新到各健康服务
 *  - Hook Application.onCreate �?APP 启动后周期性触发广�?
 *  - 通过反射调用 Shizuku.newProcess 执行系统�?Shell 命令
 *
 * 广播目标�?
 *  - com.google.android.gms.fitness.ACTION_STEP_UPDATE
 *  - com.huawei.health.action.STEP_UPDATE
 *  - com.xiaomi.hm.health.action.STEP_UPDATE
 *  - com.tencent.mm.plugin.sport.action.STEP_UPDATE（微信运动）
 *  - com.eg.android.AlipayGphone.stepRunModule.action.STEP_UPDATE（支付宝运动�?
 *
 * 硬性限制：
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - 广播 Action 名称可能�?APP 版本变化，多候选兜�?
 */
object ShizukuStepBridgeHook {

    /** 各健康APP步数广播候�?Action */
    private val broadcastActions = listOf(
        "com.google.android.gms.fitness.ACTION_STEP_UPDATE",
        "com.huawei.health.action.STEP_UPDATE",
        "com.xiaomi.hm.health.action.STEP_UPDATE",
        "com.tencent.mm.plugin.sport.action.STEP_UPDATE",
        "com.eg.android.AlipayGphone.stepRunModule.action.STEP_UPDATE",
        "com.keepfitness.action.STEP_UPDATE",
        "com.codoon.gps.action.STEP_UPDATE",
        "com.joyrun.gps.action.STEP_UPDATE"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.shizukuStepBridgeEnabled) return
        LogX.i("Shizuku 步数广播桥接 Hook 启动（Root 实验性）")

        broadcastAllStepUpdates(cfg)
        hookAppLifecycleForBroadcast(lpparam, cfg)
    }

    /** 遍历所有广�?Action 发送步数更�?*/
    private fun broadcastAllStepUpdates(cfg: StepConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过步数广播")
            return
        }
        var sentCount = 0
        for (action in broadcastActions) {
            try {
                val cmd = "am broadcast -a $action --ei steps ${cfg.customSteps}"
                val ok = ShizukuHelper.execShellSilent(cmd)
                if (ok) {
                    LogX.d("广播发送成�? $action �?${cfg.customSteps} �?)
                    sentCount++
                }
            } catch (e: Exception) { LogX.w("广播 $action 异常: ${e.message}") }
        }
        LogX.i("步数广播完成: 成功 $sentCount / ${broadcastActions.size} �?Action")
    }

    /** Hook Application.onCreate �?APP 启动后触发广�?*/
    private fun hookAppLifecycleForBroadcast(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            LogX.d("APP 启动 �?触发 Shizuku 步数广播")
                            broadcastAllStepUpdates(cfg)
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(ShizukuBridge)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate(ShizukuBridge)", e)
        }
    }
}
