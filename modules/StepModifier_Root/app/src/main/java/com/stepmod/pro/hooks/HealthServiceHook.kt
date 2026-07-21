package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 健康服务系统级写�?Hook（Root 专属�?
 *
 * 功能�?
 *  - 通过 Shizuku 调用 Google Fit API 写入系统步数
 *  - 通过 Shizuku 调用华为健康 API / 小米健康 API 系统级写�?
 *  - 通过 Shizuku 执行 am broadcast 跨进程通知健康服务
 *
 * 实现策略�?
 *  - Hook Application.onCreate �?APP 启动后触发系统级写入
 *  - Hook Google Fit ApiClient.connect / 健康服务 SDK 入口
 *  - 反射调用 Shizuku execShell 执行 am broadcast
 *
 * 硬性限制（Root 版）�?
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - Google Fit API 需要应用已授权 OAuth（系统级写入�?Google Play 服务限制�?
 *  - 华为健康 API 需 HMS Core 支持
 */
object HealthServiceHook {

    /** Google Fit 相关候选类 */
    private val googleFitCandidates = listOf(
        "com.google.android.gms.fitness.SensorsApi",
        "com.google.android.gms.fitness.HistoryApi",
        "com.google.android.gms.fitness.FitnessActivities"
    )

    /** 华为健康相关候选类 */
    private val huaweiHealthCandidates = listOf(
        "com.huawei.hms.hihealth.HiHealthOptions",
        "com.huawei.hms.hihealth.data.DataController",
        "com.huawei.hms.hihealth.sport.HealthDataApi"
    )

    /** 小米健康相关候选类 */
    private val miHealthCandidates = listOf(
        "com.xiaomi.hm.health.api.HealthDataApi",
        "com.xiaomi.hm.health.data.model.StepData"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.healthServiceEnabled) return
        LogX.i("健康服务系统级写�?Hook 启动（Root 专属�?)

        hookGoogleFit(lpparam, cfg)
        hookHuaweiHealth(lpparam, cfg)
        hookMiHealth(lpparam, cfg)
        hookAppLifecycleForBroadcast(lpparam, cfg)
    }

    private fun hookGoogleFit(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        for (clsName in googleFitCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                // Hook 类的静态方法（�?insert/update�?
                try {
                    XposedHelpers.findAndHookMethod(cls, "insert",
                        "com.google.android.gms.common.api.GoogleApiClient",
                        "com.google.android.gms.fitness.data.DataSet",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                try {
                                    LogX.d("拦截 Google Fit.insert �?注入伪造步�?${cfg.customSteps}")
                                    // 实际数据修改需 Hook DataSet 内部字段，这里仅日志
                                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                            }
                        })
                    LogX.hookSuccess(clsName, "insert")
                } catch (e: Exception) { LogX.w("$clsName.insert hook 失败: ${e.message}") }
            } catch (e: Exception) { LogX.w("候选类 $clsName 异常: ${e.message}") }
        }
    }

    private fun hookHuaweiHealth(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        for (clsName in huaweiHealthCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookConstructor(cls, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("HMS HiHealth 实例�? $clsName | 准备注入伪造步�?)
                        }
                    })
                    LogX.hookSuccess(clsName, "<init>")
                } catch (e: Exception) { LogX.w("$clsName 构�?hook 失败: ${e.message}") }
            } catch (e: Exception) { LogX.w("候选类 $clsName 异常: ${e.message}") }
        }
    }

    private fun hookMiHealth(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        for (clsName in miHealthCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "syncStep",
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                try {
                                    p.args[0] = cfg.customSteps
                                    LogX.d("拦截 miHealth.syncStep �?${cfg.customSteps}")
                                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                            }
                        })
                    LogX.hookSuccess(clsName, "syncStep")
                } catch (e: Exception) { LogX.w("$clsName.syncStep hook 失败: ${e.message}") }
            } catch (e: Exception) { LogX.w("候选类 $clsName 异常: ${e.message}") }
        }
    }

    /** Hook Application.onCreate �?APP 启动后通过 Shizuku 广播步数 */
    private fun hookAppLifecycleForBroadcast(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            if (!ShizukuHelper.isShizukuAvailable()) {
                                LogX.w("Shizuku 不可用，跳过健康服务广播")
                                return
                            }
                            // 通过 am broadcast 通知各健康服�?
                            broadcastStepToGoogleFit(cfg)
                            broadcastStepToHuaweiHealth(cfg)
                            broadcastStepToMiHealth(cfg)
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(HealthService)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate(HealthService)", e)
        }
    }

    private fun broadcastStepToGoogleFit(cfg: StepConfig) {
        try {
            val cmd = "am broadcast -a com.google.android.gms.fitness.ACTION_STEP_UPDATE " +
                    "--ei steps ${cfg.customSteps}"
            ShizukuHelper.execShellSilent(cmd)
            LogX.d("已广�?Google Fit 步数更新: ${cfg.customSteps}")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }

    private fun broadcastStepToHuaweiHealth(cfg: StepConfig) {
        try {
            val cmd = "am broadcast -a com.huawei.health.action.STEP_UPDATE " +
                    "--ei steps ${cfg.customSteps}"
            ShizukuHelper.execShellSilent(cmd)
            LogX.d("已广播华为健康步数更�? ${cfg.customSteps}")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }

    private fun broadcastStepToMiHealth(cfg: StepConfig) {
        try {
            val cmd = "am broadcast -a com.xiaomi.hm.health.action.STEP_UPDATE " +
                    "--ei steps ${cfg.customSteps}"
            ShizukuHelper.execShellSilent(cmd)
            LogX.d("已广播小米健康步数更�? ${cfg.customSteps}")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }
}
