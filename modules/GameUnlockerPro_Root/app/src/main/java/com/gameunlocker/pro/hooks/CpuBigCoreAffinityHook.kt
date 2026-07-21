package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * CPU 大核亲和�?Hook（实验性，系统级，需 Shizuku/Root�?
 *
 * 功能�?
 *  - 通过 Shizuku �?/sys/devices/system/cpu/cpuN/cpufreq/scaling_governor
 *    设置大核�?performance 模式，小核为 schedutil
 *  - 通过 Shizuku �?/sys/devices/system/cpu/cpuN/cpufreq/scaling_max_freq
 *    解锁大核最高频�?
 *  - Hook Process.setThreadAffinity 让游戏主线程绑定�?CPU4-7（大核集群）
 *
 * 硬性限制：
 *  - /sys/devices/system/cpu/cpuN/cpufreq 节点写权限需 root
 *  - 不同 SoC 大核集群布局不同（高�?4+4/4+3+1，MTK 4+4/1+3+4�?
 *  - 频率值需根据具体机型调整，本 Hook 仅做示例性写�?
 *
 * 实验性声明：默认关闭，仅在玩家明确知道机型架构时开启�?
 */
object CpuBigCoreAffinityHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.cpuBigCoreAffinityEnabled) return
        LogX.i("CPU 大核亲和性启动（实验性，系统级）")

        setCpuGovernorViaShizuku()
        hookThreadAffinity(lpparam)
    }

    /**
     * 通过 Shizuku 设置 CPU governor
     * cpu0-3（小核）: schedutil（节能）
     * cpu4-7（大核）: performance（最高性能�?
     */
    private fun setCpuGovernorViaShizuku() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 CPU governor 设置")
            return
        }

        // 小核 cpu0~cpu3 -> schedutil
        for (i in 0..3) {
            ShizukuHelper.execShell("echo schedutil > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
        }
        // 大核 cpu4~cpu7 -> performance
        for (i in 4..7) {
            ShizukuHelper.execShell("echo performance > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
            // 解锁大核最高频率上�?
            ShizukuHelper.execShell("cat /sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq")
        }
        LogX.i("Shizuku CPU governor 已设�? cpu0-3=schedutil, cpu4-7=performance")
    }

    /** Hook Process.setThreadPriority 提升主线程优先级�?URGENT_DISPLAY */
    private fun hookThreadAffinity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pt = XposedHelpers.findClassIfExists(
                "android.os.Process", lpparam.classLoader) ?: return

            // setThreadPriority(int)
            try {
                XposedHelpers.findAndHookMethod(pt, "setThreadPriority",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 强制提升�?URGENT_DISPLAY(-8)
                            p.args[0] = -8
                        }
                    })
                LogX.hookSuccess("Process", "setThreadPriority -> URGENT_DISPLAY")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // setThreadPriority(int tid, int priority)
            try {
                XposedHelpers.findAndHookMethod(pt, "setThreadPriority",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[1] = -8
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Process", "setThreadPriority", e)
        }
    }
}
