package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 防通知撤回 Hook（Root �?- 应用进程�?+ 系统级）
 *
 * 功能：阻止应用主�?cancel 自己发出的通知（防撤回提示被清掉）�?
 *
 * 拦截路径�?
 *  1. NotificationManager.cancel(int id)
 *  2. NotificationManager.cancel(String tag, int id)
 *  3. NotificationManager.cancelAll()
 *  4. NotificationManager.cancelAsUser(...)
 *
 * Root 版还可配�?NotifyListenerServiceHook 拦截系统�?onNotificationRemoved�?
 */
object AntiRecallNotifyHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.antiRecallNotifyEnabled) return
        if (isApplied) return
        isApplied = true

        LogX.i("防通知撤回启动（应用自�?cancel 全部拦截�?)

        hookCancel(lpparam)
    }

    private fun hookCancel(lpparam: XC_LoadPackage.LoadPackageParam) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return

        // cancel(int id)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancel",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancel(id=${p.args[0]})")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancel(id)")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "cancel(id)", e) }

        // cancel(String tag, int id)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancel",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancel(tag=${p.args[0]}, id=${p.args[1]})")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancel(tag, id)")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "cancel(tag, id)", e) }

        // cancelAll()
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancelAll",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancelAll()")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancelAll")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "cancelAll", e) }

        // cancelAsUser(String tag, int id, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancelAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.os.UserHandle",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("防撤回：拦截 cancelAsUser")
                        p.result = null
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancelAsUser")
        } catch (e: Exception) { LogX.w("cancelAsUser 不存在或 Hook 失败: ${e.message}") }
    }

    fun release() {
        isApplied = false
    }
}
