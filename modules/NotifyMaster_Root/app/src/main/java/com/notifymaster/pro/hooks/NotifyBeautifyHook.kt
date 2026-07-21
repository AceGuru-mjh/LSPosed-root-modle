package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通知美化 Hook（Root �?- 应用进程内）
 *
 * 功能：Hook Notification.Builder.build，修改通知样式�?
 *  - 设置颜色（colorAccent�?
 *  - 修改标题前缀
 *  - 可选覆盖图�?
 */
object NotifyBeautifyHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notifyBeautifyEnabled) return
        LogX.i("通知美化启动（颜�?${Integer.toHexString(cfg.beautifyColor)} 前缀=\"${cfg.beautifyTitlePrefix}\"�?)

        hookBuild(lpparam, cfg)
    }

    private fun hookBuild(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            val notif = p.result ?: return
                            applyColorToNotification(notif, cfg)
                            applyTitlePrefixToNotification(notif, cfg)
                        } catch (e: Throwable) {
                            LogX.w("通知美化 build 后处理异�? ${e.message}")
                        }
                    }

                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            try {
                                XposedHelpers.callMethod(builder, "setColor", cfg.beautifyColor)
                            } catch (e: Throwable) { LogX.w("setColor 异常: ${e.message}") }
                            if (cfg.beautifyTitlePrefix.isNotEmpty()) {
                                try {
                                    val extras = XposedHelpers.callMethod(builder, "getExtras")
                                    val origTitle = XposedHelpers.callMethod(extras, "getCharSequence", "android.title") as? CharSequence
                                    if (origTitle != null) {
                                        val newTitle = cfg.beautifyTitlePrefix + origTitle.toString()
                                        XposedHelpers.callMethod(extras, "putCharSequence", "android.title", newTitle as CharSequence)
                                    }
                                } catch (e: Throwable) { LogX.w("标题前缀设置异常: ${e.message}") }
                            }
                        } catch (e: Throwable) {
                            LogX.w("通知美化 build 前处理异�? ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build", e) }
    }

    private fun applyColorToNotification(notif: Any, cfg: NotifyConfig) {
        try {
            XposedHelpers.setIntField(notif, "color", cfg.beautifyColor)
        } catch (_: Throwable) { }
    }

    private fun applyTitlePrefixToNotification(notif: Any, cfg: NotifyConfig) {
        if (cfg.beautifyTitlePrefix.isEmpty()) return
        try {
            val extras = XposedHelpers.callMethod(notif, "getExtras") ?: return
            val origTitle = XposedHelpers.callMethod(extras, "getCharSequence", "android.title") as? CharSequence ?: return
            val newTitle = cfg.beautifyTitlePrefix + origTitle.toString()
            XposedHelpers.callMethod(extras, "putCharSequence", "android.title", newTitle as CharSequence)
        } catch (_: Throwable) { }
    }
}
