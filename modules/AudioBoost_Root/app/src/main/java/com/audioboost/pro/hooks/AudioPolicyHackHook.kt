package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 修改AudioPolicy配置(最大音量增益)（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object AudioPolicyHackHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.audioPolicyHackEnabled) return
        LogX.i("AudioPolicyHackHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过AudioPolicyHackHook")
                            return
                        }
                        execute()
                        LogX.i("AudioPolicyHackHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("AudioPolicyHackHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->AudioPolicyHackHook")
    }

    private fun execute() {
        val sh = ShizukuHelper
        if (!sh.createMagiskOverlay("audioboost")) {
            LogX.w("Magisk overlay 创建失败，无 root 权限或 Magisk 未安装")
            return
        }

        val realAudioPolicy = """
<?xml version="1.0" encoding="UTF-8"?>
<audio_policy_configuration version="1.0">
    <global_configuration speaker_drc_enabled="true"/>
    <modules>
        <module name="primary" halVersion="3.0">
            <mixPorts>
                <mixPort name="primary output" role="source">
                    <profile name="boosted" format="AUDIO_FORMAT_PCM_16_BIT"
                        samplingRates="48000" channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
                </mixPort>
            </mixPorts>
            <devicePorts>
                <devicePort tagName="Speaker" type="AUDIO_DEVICE_OUT_SPEAKER" role="sink">
                    <profile name="boosted" format="AUDIO_FORMAT_PCM_16_BIT"
                        samplingRates="48000" channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
                    <gains>
                        <gain name="gain_1" mode="AUDIO_GAIN_MODE_JOINT" 
                            minValueMB="-3200" maxValueMB="600" defaultValueMB="600" stepValueMB="100"/>
                    </gains>
                </devicePort>
            </devicePorts>
        </module>
    </modules>
    <volumes>
        <volume stream="AUDIO_STREAM_MUSIC" deviceCategory="DEVICE_CATEGORY_SPEAKER">
            <point>1;-4500</point><point>33;-2500</point><point>66;-1000</point><point>100;0</point>
        </volume>
        <volume stream="AUDIO_STREAM_MUSIC" deviceCategory="DEVICE_CATEGORY_HEADSET">
            <point>1;-4000</point><point>33;-2000</point><point>66;-500</point><point>100;0</point>
        </volume>
    </volumes>
</audio_policy_configuration>
        """.trimIndent()

        sh.writeMagiskOverlay("audioboost", "etc/audio_policy_configuration.xml", realAudioPolicy)
        LogX.i("AudioPolicy overlay 已写入 Magisk 模块（+6dB 硬件增益）")

        // mount --bind 覆盖原文件
        sh.execShellSilent("mount --bind /data/adb/modules/audioboost/system/etc/audio_policy_configuration.xml /vendor/etc/audio_policy_configuration.xml 2>/dev/null")
        LogX.d("[root] mount --bind audio_policy_configuration.xml")

        // 重启 audioserver 使配置生效
        sh.execShellSilent("killall audioserver 2>/dev/null")
        LogX.i("[root] audioserver 已重启，AudioPolicy 配置生效")
    }
}
