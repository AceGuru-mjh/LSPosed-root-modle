package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * tinymix 音频混音器探测（Root 专属�? *
 * 通过 Shizuku 执行 tinymix 获取/设置 ALSA 混音器控件�? * 用于硬件级音量、增益、路由控制�? * 硬性限制：需 Shizuku root 级授�?+ 设备支持 tinymix 二进�? */
object TinymixAudioHook {

    private val tinymixBins = listOf(
        "tinymix",
        "/system/bin/tinymix",
        "/vendor/bin/tinymix",
        "/system/xbin/tinymix",
        "/odm/bin/tinymix",
        "/system_ext/bin/tinymix"
    )

    private val mixerControls = listOf(
        "RX1 Digital Volume",
        "RX2 Digital Volume",
        "RX3 Digital Volume",
        "Speaker Volume",
        "Headphone Volume",
        "PCM Playback Volume",
        "HPHL Volume",
        "HPHR Volume",
        "LINEOUT1 Volume",
        "LINEOUT2 Volume",
        "ADC1 Volume",
        "ADC2 Volume",
        "MIC Gain",
        "DEC1 Volume",
        "DEC2 Volume",
        "IIR1 INP1 Volume"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.tinymixProbeEnabled) return
        LogX.i("TinymixAudioHook 启动（Root 专属�?)

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过TinymixAudioHook")
                            return
                        }
                        execute(cfg)
                        LogX.i("TinymixAudioHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("TinymixAudioHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->TinymixAudioHook")
    }

    private fun execute(cfg: AudioConfig) {
        val sh = ShizukuHelper

        // 1. 探测 tinymix 二进制路�?        val tinymixPath = findTinymixPath(sh)
        if (tinymixPath == null) {
            LogX.w("[root] 设备不支�?tinymix，跳过混音器控制")
            return
        }
        LogX.i("[root] tinymix 路径: $tinymixPath")

        // 2. 获取所有混音器控件
        val mixerList = sh.execShell("$tinymixPath 2>/dev/null")
        if (mixerList != null) {
            LogX.d("[root] tinymix 控件列表: ${mixerList.take(500)}")

            // 3. 对已知控件进行增�?            for (control in mixerControls) {
                if (mixerList.contains(control)) {
                    val boost = (cfg.boostLevel / 10).coerceIn(1, 15)
                    sh.execShellSilent("$tinymixPath \"$control\" $boost 2>/dev/null")
                    LogX.d("[root] tinymix $control -> $boost")
                }
            }

            // 4. 额外探测每路扬声�?            probeExtraControls(sh, tinymixPath, mixerList)
        }
    }

    private fun findTinymixPath(sh: ShizukuHelper): String? {
        for (bin in tinymixBins) {
            val test = sh.execShell("test -x $bin && echo OK 2>/dev/null")
            if (test?.contains("OK") == true) return bin
        }
        // 回退：which
        val which = sh.execShell("which tinymix 2>/dev/null")?.trim()
        if (!which.isNullOrEmpty()) return which
        return null
    }

    private fun probeExtraControls(sh: ShizukuHelper, tinymixPath: String, mixerList: String) {
        val extraControls = listOf(
            "Earphone Gain",
            "Headset Gain",
            "Speaker Gain",
            "DAC1 Switch",
            "DAC2 Switch"
        )
        for (control in extraControls) {
            if (mixerList.contains(control)) {
                val valOut = sh.execShell("$tinymixPath \"$control\" 2>/dev/null")
                if (valOut != null) {
                    LogX.d("[root] tinymix $control 当前�? ${valOut.trim()}")
                }
            }
        }
    }
}
