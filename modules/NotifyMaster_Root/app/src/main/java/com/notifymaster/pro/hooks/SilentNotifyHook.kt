package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 静默通知 Hook（实验�?- Root 版应用进程内�?
 *
 * 功能：让指定 APP 发出的通知静默（不响铃不震动）�?
 */
object SilentNotifyHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.silentNotifyEnabled) return
        if (lpparam.packageName !in cfg.silentTargetApps) {
            LogX.d("静默通知：当�?APP ${lpparam.packageName} 不在静默列表，跳�?)
            return
        }
        LogX.i("静默通知启动（实验性，�?${lpparam.packageName} 生效�?)

        hookBuilderBuild(lpparam)
        hookNotificationChannel(lpparam)
    }

    private fun hookBuilderBuild(lpparam: XC_LoadPackage.LoadPackageParam) {
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            try {
                                XposedHelpers.callMethod(builder, "setDefaults", 0)
                            } catch (e: Throwable) { LogX.w("setDefaults 异常: ${e.message}") }
                            try {
                                XposedHelpers.callMethod(builder, "setSound", null as Any?)
                            } catch (e: Throwable) { LogX.w("setSound 异常: ${e.message}") }
                            try {
                                XposedHelpers.callMethod(builder, "setVibrate", null as Any?)
                            } catch (e: Throwable) { LogX.w("setVibrate 异常: ${e.message}") }
                            try {
                                XposedHelpers.callMethod(builder, "setPriority", 1)
                            } catch (e: Throwable) { LogX.w("setPriority(LOW) 异常: ${e.message}") }
                        } catch (e: Throwable) {
                            LogX.w("静默 build 前异�? ${e.message}")
                        }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val notif = p.result ?: return
                            XposedHelpers.setIntField(notif, "defaults", 0)
                            XposedHelpers.setObjectField(notif, "sound", null)
                            XposedHelpers.setObjectField(notif, "vibrate", null)
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build[silent]")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build[silent]", e) }
    }

    private fun hookNotificationChannel(lpparam: XC_LoadPackage.LoadPackageParam) {
        val channelCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationChannel", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookConstructor(
                channelCls,
                String::class.java,
                CharSequence::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            p.args[2] = 2 // IMPORTANCE_LOW
                            LogX.d("静默：通知渠道 importance 强制�?LOW(2)")
                        } catch (e: Throwable) { LogX.w("渠道构造静默异�? ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationChannel", "<init>[silent]")
        } catch (e: Exception) { LogX.w("NotificationChannel 构�?Hook 失败: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(
                channelCls, "setSound",
                "android.net.Uri",
                "android.media.AudioAttributes",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.args[0] = null
                    }
                })
            LogX.hookSuccess("NotificationChannel", "setSound")
        } catch (e: Exception) { LogX.w("setSound Hook 失败: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(
                channelCls, "setVibrationPattern",
                LongArray::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.args[0] = null
                    }
                })
            LogX.hookSuccess("NotificationChannel", "setVibrationPattern")
        } catch (e: Exception) { LogX.w("setVibrationPattern Hook 失败: ${e.message}") }
    }
}
