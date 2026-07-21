package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 麦克风增益增强Hook（实验性，仅应用层�?
 *
 * 拦截路径�?
 *  - AudioRecord.read(short[], int, int) / read(short[], int, int, int)
 *  - AudioRecord.read(byte[], int, int)
 *
 * 对读取到�?16bit PCM 样本做整数倍增益（�?micBoostLevel 控制�?
 */
object MicBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.micBoostEnabled) return
        LogX.i("麦克风增益启动（实验性） boost=${cfg.micBoostLevel}%")

        hookAudioRecordReadShortArray(lpparam, cfg)
        hookAudioRecordReadByteArray(lpparam, cfg)
    }

    private fun hookAudioRecordReadShortArray(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord", lpparam.classLoader) ?: return
            val gain = cfg.micBoostLevel / 100f
            try {
                XposedHelpers.findAndHookMethod(cls, "read",
                    ShortArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val buf = p.args[0] as? ShortArray ?: return
                            val offset = (p.args[1] as? Int) ?: 0
                            val readCount = (p.result as? Int) ?: return
                            if (readCount <= 0) return
                            for (i in offset until offset + readCount) {
                                if (i >= buf.size) break
                                val amp = (buf[i].toInt() * gain).toInt()
                                buf[i] = amp.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "read(short[])")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cls, "read",
                    ShortArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val buf = p.args[0] as? ShortArray ?: return
                            val offset = (p.args[1] as? Int) ?: 0
                            val readCount = (p.result as? Int) ?: return
                            if (readCount <= 0) return
                            for (i in offset until offset + readCount) {
                                if (i >= buf.size) break
                                val amp = (buf[i].toInt() * gain).toInt()
                                buf[i] = amp.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "read(short[],readMode)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioRecord", "read(short[])", e)
        }
    }

    private fun hookAudioRecordReadByteArray(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord", lpparam.classLoader) ?: return
            val gain = cfg.micBoostLevel / 100f
            try {
                XposedHelpers.findAndHookMethod(cls, "read",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val buf = p.args[0] as? ByteArray ?: return
                            val offset = (p.args[1] as? Int) ?: 0
                            val readCount = (p.result as? Int) ?: return
                            if (readCount <= 0 || readCount % 2 != 0) return
                            var i = offset
                            val end = offset + readCount
                            while (i + 1 < end) {
                                val lo = buf[i].toInt() and 0xFF
                                val hi = buf[i + 1].toInt() shl 8
                                var sample = (lo or hi).toShort().toInt()
                                sample = (sample * gain).toInt()
                                sample = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                buf[i] = (sample and 0xFF).toByte()
                                buf[i + 1] = ((sample shr 8) and 0xFF).toByte()
                                i += 2
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord", "read(byte[])")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioRecord", "read(byte[])", e)
        }
    }
}
