package com.audioboost.pro

import android.app.Application
import android.util.Log
import com.audioboost.pro.models.AudioConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-AudioBoost-Root"
        const val VERSION = "1.0.12"
        var currentPkg: String? = null
    }

    private fun tryInvoke(className: String, method: String, loader: ClassLoader, lpparam: XC_LoadPackage.LoadPackageParam, cfg: Any) {
        try {
            val cls = Class.forName(className, false, loader)
            cls.getDeclaredMethod(method, XC_LoadPackage.LoadPackageParam::class.java, cfg.javaClass)
                .invoke(null, lpparam, cfg)
        } catch (e: Throwable) {
            Log.e(TAG, "$className.$method FAIL: ${e.message}")
        }
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        Log.i(TAG, "AudioBoost Pro v$VERSION 初始化 | LSPosed Root 模式")
        try {
            Class.forName("com.audioboost.pro.utils.ShizukuHelper")
                .getDeclaredMethod("isShizukuAvailable").invoke(null)
        } catch (_: Throwable) {}
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== AudioBoost v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
        currentPkg = pkg

        Log.i(TAG, "环境: ${if (local) "本地模式" else "集成模式"}")
        if (checkConflict(lpparam)) {
            Log.w(TAG, "检测到模块冲突，部分功能已禁用")
            addLogStore("warn", "模块冲突检测触发")
        }

        initConfig(lpparam)
        if (!local) {
            try { Thread.sleep(100) } catch (_: Throwable) { }
        }

        val cfg = loadConfig()
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} 音量=${cfg.volumeBoostEnabled} " +
                "低音=${cfg.bassBoostEnabled} 均衡器=${cfg.equalizerEnabled} " +
                "[实验]扬声器=${cfg.speakerBoostEnabled} 麦克风=${cfg.micBoostEnabled} 音质=${cfg.audioQualityEnhanceEnabled} " +
                "[Root]系统音量=${cfg.systemVolumeBoostEnabled} AudioFlinger=${cfg.audioFlingerNodeEnabled} " +
                "[Root实验]AudioPolicy=${cfg.globalAudioPolicyEnabled} ShizukuBridge=${cfg.shizukuAudioBridgeEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.audioboost.pro.hooks."

        if (cfg.volumeBoostEnabled) tryInvoke(HP + "VolumeBoostHook", "apply", loader, lpparam, cfg)
        if (cfg.bassBoostEnabled) tryInvoke(HP + "BassBoostHook", "apply", loader, lpparam, cfg)
        if (cfg.equalizerEnabled) tryInvoke(HP + "EqualizerHook", "apply", loader, lpparam, cfg)

        if (cfg.speakerBoostEnabled) tryInvoke(HP + "SpeakerBoostHook", "apply", loader, lpparam, cfg)
        if (cfg.micBoostEnabled) tryInvoke(HP + "MicBoostHook", "apply", loader, lpparam, cfg)
        if (cfg.audioQualityEnhanceEnabled) tryInvoke(HP + "AudioQualityEnhanceHook", "apply", loader, lpparam, cfg)

        if (cfg.systemVolumeBoostEnabled) tryInvoke(HP + "SystemVolumeHook", "apply", loader, lpparam, cfg)
        if (cfg.audioFlingerNodeEnabled) tryInvoke(HP + "AudioFlingerHook", "apply", loader, lpparam, cfg)

        if (cfg.globalAudioPolicyEnabled) tryInvoke(HP + "GlobalAudioPolicyHook", "apply", loader, lpparam, cfg)
        if (cfg.shizukuAudioBridgeEnabled) tryInvoke(HP + "ShizukuAudioBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.audioPolicyHackEnabled) tryInvoke(HP + "AudioPolicyHackHook", "apply", loader, lpparam, cfg)
        if (cfg.tinymixProbeEnabled) tryInvoke(HP + "TinymixAudioHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.audioboost.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.netease.cloudmusic", "com.tencent.wmusic", "com.kugou.android",
        "com.kuwo.player", "com.netease.cloudmusic.player", "com.spotify.music",
        "com.google.android.apps.youtube.music", "com.tencent.mm",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.miui.player", "com.hihonor.cloudmusic"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.audioboost.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.audioboost.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.audioboost.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): AudioConfig {
        try {
            Class.forName("com.audioboost.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as AudioConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.audioboost.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? AudioConfig ?: AudioConfig(packageName = "global")
        } catch (_: Throwable) { AudioConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.audioboost.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.audioboost.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.audioboost.pro.utils.LogStore")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
            }
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            Class.forName("com.audioboost.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.audioboost.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
