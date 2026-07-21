package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 音频优先级提�?Hook（实验性）
 *
 * 功能�?
 *  - Hook AudioTrack.setPerformanceMode 强制 PERFORMANCE_MODE_LOW_LATENCY
 *  - Hook AudioTrack.play / AudioRecord.startRecording 提升音频线程优先�?
 *
 * 实验性声明：对节奏游戏（音游）和射击游戏（脚步声定位）有可感知效果�?
 */
object AudioPriorityBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.audioPriorityBoostEnabled) return
        LogX.i("音频优先级提升启动（实验性）")

        hookAudioTrack(lpparam)
        hookAudioRecord(lpparam)
        boostAudioThreadPriority()
    }

    private fun hookAudioTrack(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClassIfExists(
                "android.media.AudioTrack", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(at, "setPerformanceMode",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = 2
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setPerformanceMode -> LOW_LATENCY")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(at, "play", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val pt = Class.forName("android.os.Process")
                            val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                            m.invoke(null, -16)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
                LogX.hookSuccess("AudioTrack", "play -> threadPriority AUDIO")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("AudioTrack", "setPerformanceMode", e)
        }
    }

    private fun hookAudioRecord(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ar = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(ar, "startRecording",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val pt = Class.forName("android.os.Process")
                                val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                                m.invoke(null, -16)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "startRecording -> threadPriority AUDIO")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("AudioRecord", "startRecording", e)
        }
    }

    private fun boostAudioThreadPriority() {
        try {
            val pt = Class.forName("android.os.Process")
            val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
            m.invoke(null, -16)
            LogX.d("音频线程优先级提升至 AUDIO(-16)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
