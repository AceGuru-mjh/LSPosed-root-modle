package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogStore
import com.audioboost.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 音量增强Hook（仅应用层，�?NoRoot 版相同）
 *
 * 拦截路径�?
 *  1. AudioTrack.setVolume(float) - 多媒体音轨音�?
 *  2. AudioTrack.setPlayerVolume(int, int) - 播放器音�?
 *  3. MediaPlayer.setVolume(float, float) - 媒体播放器左右声道音�?
 *
 * 注意：Root 版另�?SystemVolumeHook 通过 Shizuku 修改系统级音量�?
 */
object VolumeBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.volumeBoostEnabled) return
        LogX.i("音量增强启动 boost=${cfg.boostLevel}%")
        try { LogStore.add("boosted", "音量增强: ${cfg.boostLevel}%") } catch (_: Exception) { }
        try { LogStore.incrementCounter(1) } catch (_: Exception) { }

        hookAudioTrackSetVolume(lpparam, cfg)
        hookAudioTrackSetPlayerVolume(lpparam, cfg)
        hookMediaPlayerSetVolume(lpparam, cfg)
    }

    private fun hookAudioTrackSetVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.media.AudioTrack", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = (p.args[0] as? Float) ?: return
                            p.args[0] = clampVolume(v * cfg.boostLevel / 100f)
                            LogX.d("AudioTrack.setVolume: $v -> ${p.args[0]}")
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val l = (p.args[0] as? Float) ?: return
                            val r = (p.args[1] as? Float) ?: return
                            p.args[0] = clampVolume(l * cfg.boostLevel / 100f)
                            p.args[1] = clampVolume(r * cfg.boostLevel / 100f)
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setVolume(L,R,track)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioTrack", "setVolume", e)
        }
    }

    private fun hookAudioTrackSetPlayerVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.media.AudioTrack", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setPlayerVolume",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val l = (p.args[0] as? Int) ?: return
                            val r = (p.args[1] as? Int) ?: return
                            val max = try {
                                XposedHelpers.getStaticIntField(cls, "MAX_VOLUME_INT") as Int
                            } catch (_: Throwable) { 32767 }
                            val nl = (l.toLong() * cfg.boostLevel / 100L).toInt().coerceIn(0, max)
                            val nr = (r.toLong() * cfg.boostLevel / 100L).toInt().coerceIn(0, max)
                            p.args[0] = nl
                            p.args[1] = nr
                        }
                    })
                LogX.hookSuccess("AudioTrack", "setPlayerVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioTrack", "setPlayerVolume", e)
        }
    }

    private fun hookMediaPlayerSetVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.media.MediaPlayer", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val l = (p.args[0] as? Float) ?: return
                            val r = (p.args[1] as? Float) ?: return
                            p.args[0] = clampVolume(l * cfg.boostLevel / 100f)
                            p.args[1] = clampVolume(r * cfg.boostLevel / 100f)
                            LogX.d("MediaPlayer.setVolume: $l/$r -> ${p.args[0]}/${p.args[1]}")
                        }
                    })
                LogX.hookSuccess("MediaPlayer", "setVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cls, "setVolume",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val l = (p.args[0] as? Float) ?: return
                            val r = (p.args[1] as? Float) ?: return
                            p.args[0] = clampVolume(l * cfg.boostLevel / 100f)
                            p.args[1] = clampVolume(r * cfg.boostLevel / 100f)
                        }
                    })
                LogX.hookSuccess("MediaPlayer", "setVolume(L,R,track)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("MediaPlayer", "setVolume", e)
        }
    }

    private fun clampVolume(v: Float): Float {
        return v.coerceIn(0f, 1.0f)
    }
}
