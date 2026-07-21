package com.notifymaster.pro

import android.app.Application
import android.util.Log
import com.notifymaster.pro.models.NotifyConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "LSP-NotifyMaster-Root"
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
        Log.i(TAG, "NotifyMaster Pro v$VERSION 初始化 | Root 版 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.processName != lpparam.packageName) return
        try {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        val local = isLocalMode()
        Log.i(TAG, "=== NotifyMaster v$VERSION starting | pkg=$pkg | process=${lpparam.processName} | mode=${if (local) "local" else "integrated"} ===")
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
        cfg.packageName = pkg
        Log.i(TAG, "配置: 总开关${cfg.masterEnabled} 过滤=${cfg.notifyFilterEnabled} " +
                "防撤回=${cfg.antiRecallNotifyEnabled} 历史=${cfg.notifyHistoryEnabled} " +
                "美化=${cfg.notifyBeautifyEnabled} [实验]分组=${cfg.batchNotifyEnabled} " +
                "优先级=${cfg.priorityOverrideEnabled} 静默=${cfg.silentNotifyEnabled} " +
                "[Root]系统通知=${cfg.systemNotifyHookEnabled} Listener=${cfg.notifyListenerHookEnabled} " +
                "全局过滤=${cfg.globalNotifyFilterEnabled} Shizuku桥接=${cfg.shizukuNotifyBridgeEnabled}")

        if (!cfg.masterEnabled) {
            Log.i(TAG, "总开关关闭，跳过所有Hook")
            return
        }

        val loader = lpparam.classLoader
        val HP = "com.notifymaster.pro.hooks."

        if (cfg.notifyFilterEnabled) tryInvoke(HP + "NotifyFilterHook", "apply", loader, lpparam, cfg)
        if (cfg.antiRecallNotifyEnabled) tryInvoke(HP + "AntiRecallNotifyHook", "apply", loader, lpparam, cfg)
        if (cfg.notifyHistoryEnabled) tryInvoke(HP + "NotifyHistoryHook", "apply", loader, lpparam, cfg)
        if (cfg.notifyBeautifyEnabled) tryInvoke(HP + "NotifyBeautifyHook", "apply", loader, lpparam, cfg)

        if (cfg.batchNotifyEnabled) tryInvoke(HP + "BatchNotifyHook", "apply", loader, lpparam, cfg)
        if (cfg.priorityOverrideEnabled) tryInvoke(HP + "PriorityOverrideHook", "apply", loader, lpparam, cfg)
        if (cfg.silentNotifyEnabled) tryInvoke(HP + "SilentNotifyHook", "apply", loader, lpparam, cfg)

        if (cfg.systemNotifyHookEnabled) tryInvoke(HP + "SystemNotifyHook", "apply", loader, lpparam, cfg)
        if (cfg.notifyListenerHookEnabled) tryInvoke(HP + "NotifyListenerServiceHook", "apply", loader, lpparam, cfg)

        if (cfg.globalNotifyFilterEnabled) tryInvoke(HP + "GlobalNotifyFilterHook", "apply", loader, lpparam, cfg)
        if (cfg.shizukuNotifyBridgeEnabled) tryInvoke(HP + "ShizukuNotifyBridgeHook", "apply", loader, lpparam, cfg)

        if (cfg.globalNotificationQueueEnabled) tryInvoke(HP + "GlobalNotificationQueueHook", "apply", loader, lpparam, cfg)

        if (cfg.sysfsLedEnabled) tryInvoke(HP + "SysfsNotifyLedHook", "apply", loader, lpparam, cfg)

        hookAppLifecycle(lpparam)
        Log.i(TAG, "===== 全部Hook就绪: $pkg =====")
        } catch (e: Throwable) {
            Log.e(TAG, "模块崩溃防护: ${lpparam.packageName}", e)
            try { addLogStore("error", "模块异常: ${e.message}") } catch (_: Exception) { }
            try {
                Class.forName("com.notifymaster.pro.utils.AntiDetectionHelper")
                    .getDeclaredMethod("sleepDuringVerify").invoke(null)
            } catch (_: Throwable) {}
        }
    }

    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone", "com.taobao.taobao",
        "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.netease.cloudmusic", "com.tencent.wmusic",
        "com.sina.weibo", "com.zhihu.android",
        "com.baidu.searchbox", "com.ss.android.article.news"
    )

    private fun isLocalMode(): Boolean {
        return try {
            Class.forName("com.notifymaster.pro.utils.EnvDetector")
                .getDeclaredMethod("isLocalMode").invoke(null) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun checkConflict(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return try {
            Class.forName("com.notifymaster.pro.utils.ModuleConflictDetector")
                .getDeclaredMethod("checkConflict", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun addLogStore(level: String, msg: String) {
        try {
            Class.forName("com.notifymaster.pro.utils.LogStore")
                .getDeclaredMethod("add", String::class.java, String::class.java)
                .invoke(null, level, msg)
        } catch (_: Throwable) {}
    }

    private fun loadConfig(): NotifyConfig {
        try {
            Class.forName("com.notifymaster.pro.utils.HookConfigReader")
                .getDeclaredMethod("readGlobal").invoke(null)?.let { return it as NotifyConfig }
        } catch (_: Throwable) {}
        return try {
            Class.forName("com.notifymaster.pro.utils.ConfigManager")
                .getDeclaredMethod("getGlobalConfig").invoke(null) as? NotifyConfig ?: NotifyConfig(packageName = "global")
        } catch (_: Throwable) { NotifyConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            Class.forName("com.notifymaster.pro.utils.EnvDetector")
                .getDeclaredMethod("detect", XC_LoadPackage.LoadPackageParam::class.java)
                .invoke(null, lpparam)
        } catch (_: Throwable) {}
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) {
                Class.forName("com.notifymaster.pro.utils.ConfigManager")
                    .getDeclaredMethod("init", android.content.Context::class.java)
                    .invoke(null, app)
                Class.forName("com.notifymaster.pro.utils.LogStore")
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
                            Class.forName("com.notifymaster.pro.utils.ConfigManager")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                            Class.forName("com.notifymaster.pro.utils.LogStore")
                                .getDeclaredMethod("init", android.content.Context::class.java)
                                .invoke(null, app)
                        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { Log.w(TAG, "异常: ${e.message}") }
    }
}
