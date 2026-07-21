package com.audioboost.pro.hooks

import android.app.Application
import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AudioPolicy 全局修改Hook（实验性，Root 版专属）
 *
 * 功能�?
 *  - 通过 Shizuku 修改 AudioPolicy 配置
 *  - 修改 /vendor/etc/audio_policy_configuration.xml �?/system/etc/audio_policy_configuration.xml
 *  - 强制设置默认采样率到 audioPolicySampleRate
 *
 * 硬性限制：
 *  - 必须先检�?ShizukuHelper.isShizukuAvailable()
 *  - 修改系统文件需 root 级别 Shizuku 授权
 *  - 修改后需重启 audioserver 才能生效（部分设备重启才生效�?
 *  - 实验性功能，可能导致系统音频异常，谨慎使�?
 */
object GlobalAudioPolicyHook {

    private const val POLICY_PATH_1 = "/vendor/etc/audio_policy_configuration.xml"
    private const val POLICY_PATH_2 = "/system/etc/audio_policy_configuration.xml"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.globalAudioPolicyEnabled) return
        LogX.i("AudioPolicy 修改启动（实验性） targetSampleRate=${cfg.audioPolicySampleRate}")

        // Hook Application.onCreate 触发修改
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { applyAudioPolicy(cfg) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(AudioPolicy)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // Hook AudioManager.getProperty 拦截采样率查询返回伪造�?
        hookAudioManagerGetProperty(lpparam, cfg)
    }

    /** 通过 Shizuku 修改 AudioPolicy 配置 */
    private fun applyAudioPolicy(cfg: AudioConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 AudioPolicy 修改")
            return
        }
        try {
            // 先备份原文件
            for (path in listOf(POLICY_PATH_1, POLICY_PATH_2)) {
                val exists = ShizukuHelper.execShell("test -f $path && echo yes")?.trim()
                if (exists == "yes") {
                    // 备份
                    ShizukuHelper.execShellSilent("cp -n $path ${path}.bak")
                    // 替换 samplingRate
                    val targetRate = cfg.audioPolicySampleRate
                    // �?sed 替换 samplingRate="xxx" 属�?
                    ShizukuHelper.execShellSilent(
                        "sed -i 's/samplingRate=\"[0-9]*\"/samplingRate=\"$targetRate\"/g' $path"
                    )
                    LogX.i("AudioPolicy 已修�?$path samplingRate=$targetRate")
                    // 重启 audioserver
                    ShizukuHelper.execShellSilent("killall audioserver 2>/dev/null")
                    LogX.i("audioserver 已重启（部分设备需手动重启�?)
                    return
                }
            }
            LogX.w("未找�?audio_policy_configuration.xml，跳�?)
        } catch (e: Throwable) {
            LogX.e("AudioPolicy 修改异常", e)
        }
    }

    /** Hook AudioManager.getProperty 返回伪造的采样率（应用层显示） */
    private fun hookAudioManagerGetProperty(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getProperty",
                    String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            if (key == "android.media.property.OUTPUT_SAMPLE_RATE") {
                                p.result = cfg.audioPolicySampleRate.toString()
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getProperty")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "getProperty", e)
        }
    }
}
