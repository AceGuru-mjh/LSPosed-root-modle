package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统 NotificationListenerService Hook（Root 专属 - 需 LSPosed 框架加载�?system_server 或对应进程）
 *
 * 功能：Hook NotificationListenerService 的回调方法，全局监听所�?APP 的通知�?
 *  - onNotificationPosted(StatusBarNotification)
 *  - onNotificationRemoved(StatusBarNotification)
 *  - onNotificationRankingUpdate(RankingMap)
 *
 * 拦截路径�?
 *  - 当应用自身实现了 NotificationListenerService 时，Hook 其回�?
 *  - �?system_server 中可全局监听（需 LSPosed Root 模式作用域含 android�?
 *
 * 硬性限制：
 *  - �?Hook NotificationListenerService 类的方法，不修改系统服务
 *  - onNotificationRemoved 可拦截以阻止撤回（配�?AntiRecallNotifyHook�?
 *  - �?LSPatch 模式下仅当前 APP 内的 Listener 生效，不全局
 */
object NotifyListenerServiceHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notifyListenerHookEnabled) return
        LogX.i("NotificationListenerService Hook 启动（Root 专属，全局监听�?)

        hookListenerCallbacks(lpparam, cfg)
    }

    private fun hookListenerCallbacks(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val listenerCls = XposedHelpers.findClassIfExists(
            "android.service.notification.NotificationListenerService",
            lpparam.classLoader) ?: run {
            LogX.w("NotificationListenerService 类未找到，可�?system_server 未加载到�?ClassLoader")
            return
        }

        // onNotificationPosted(StatusBarNotification)
        try {
            XposedHelpers.findAndHookMethod(
                listenerCls, "onNotificationPosted",
                "android.service.notification.StatusBarNotification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val sbn = p.args[0] ?: return
                            val pkg = XposedHelpers.callMethod(sbn, "getPackageName") as? String ?: "?"
                            val id = XposedHelpers.callMethod(sbn, "getId") as? Int ?: -1
                            if (cfg.captureAllNotifications || pkg in cfg.targetApps) {
                                LogX.d("[Listener] onNotificationPosted: pkg=$pkg id=$id")
                            }
                        } catch (e: Throwable) { LogX.w("onNotificationPosted 异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationListenerService", "onNotificationPosted")
        } catch (e: Exception) { LogX.w("onNotificationPosted Hook 失败: ${e.message}") }

        // onNotificationPosted(StatusBarNotification, RankingMap)
        try {
            XposedHelpers.findAndHookMethod(
                listenerCls, "onNotificationPosted",
                "android.service.notification.StatusBarNotification",
                "android.service.notification.NotificationListenerService.RankingMap",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val sbn = p.args[0] ?: return
                            val pkg = XposedHelpers.callMethod(sbn, "getPackageName") as? String ?: "?"
                            if (cfg.captureAllNotifications || pkg in cfg.targetApps) {
                                LogX.d("[Listener] onNotificationPosted(sbn, ranking): pkg=$pkg")
                            }
                        } catch (e: Throwable) { LogX.w("onNotificationPosted(ranking) 异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationListenerService", "onNotificationPosted(sbn, ranking)")
        } catch (e: Exception) { LogX.w("onNotificationPosted(sbn, ranking) Hook 失败: ${e.message}") }

        // onNotificationRemoved(StatusBarNotification) - 阻止撤回
        try {
            XposedHelpers.findAndHookMethod(
                listenerCls, "onNotificationRemoved",
                "android.service.notification.StatusBarNotification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val sbn = p.args[0] ?: return
                            val pkg = XposedHelpers.callMethod(sbn, "getPackageName") as? String ?: "?"
                            // 防撤回：阻止 onNotificationRemoved 被回调（系统侧）
                            if (cfg.antiRecallNotifyEnabled) {
                                LogX.d("[Listener] onNotificationRemoved 拦截: pkg=$pkg")
                                p.result = null
                            }
                        } catch (e: Throwable) { LogX.w("onNotificationRemoved 异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationListenerService", "onNotificationRemoved")
        } catch (e: Exception) { LogX.w("onNotificationRemoved Hook 失败: ${e.message}") }

        // onNotificationRankingUpdate(RankingMap)
        try {
            XposedHelpers.findAndHookMethod(
                listenerCls, "onNotificationRankingUpdate",
                "android.service.notification.NotificationListenerService.RankingMap",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("[Listener] onNotificationRankingUpdate")
                    }
                })
            LogX.hookSuccess("NotificationListenerService", "onNotificationRankingUpdate")
        } catch (e: Exception) { LogX.w("onNotificationRankingUpdate Hook 失败: ${e.message}") }
    }
}
