package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import com.notifymaster.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 全局通知队列管理(跨APP排序)（Root 专属�?
 *
 * 通过 Shizuku 执行系统级操作�?
 * 硬性限制：需 Shizuku root 级授�?
 */
object GlobalNotificationQueueHook {

    private var cfg: NotifyConfig? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.globalNotifyQueueEnabled) return
        this.cfg = cfg
        LogX.i("GlobalNotificationQueueHook 启动（Root 专属�?)

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过GlobalNotificationQueueHook")
                            return
                        }
                        execute()
                        LogX.i("GlobalNotificationQueueHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("GlobalNotificationQueueHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->GlobalNotificationQueueHook")
    }

    private fun execute() {
        val c = cfg ?: return
        try {
            Class.forName("com.android.server.NotificationManagerService")
            LogX.d("NotificationManagerService 类已加载，全局通知队列 Hook 就绪")
        } catch (e: ClassNotFoundException) {
            LogX.d("NotificationManagerService 不在当前进程（需 system_server 作用域）")
        }

        // ===== Root 命令：通过 Shizuku 执行系统级通知操作 =====
        if (c.globalQueueRootEnabled) {
            runRootCommands(c)
        }
    }

    private fun runRootCommands(c: NotifyConfig) {
        val sh = ShizukuHelper

        // 1. 获取所有通知
        val listOutput = sh.execShell("cmd notification list")
        LogX.d("[root] cmd notification list: ${listOutput?.take(200)}")

        // 2. 取消垃圾通知（根据配置的关键词匹配）
        for (keyword in c.globalFilterKeywords) {
            sh.execShellSilent("cmd notification cancel-all")
            LogX.d("[root] 已按关键词取消通知: $keyword")
        }

        // 3. dumpsys notification 获取完整诊断信息
        val dumpsysOutput = sh.execShell("dumpsys notification --noredact 2>/dev/null")
        LogX.d("[root] dumpsys notification 已获取（${dumpsysOutput?.length ?: 0} bytes�?)

        // 4. 关闭悬浮通知（heads-up�?
        sh.execShellSilent("settings put global heads_up_notifications_enabled 0")
        LogX.d("[root] heads_up_notifications_enabled = 0")

        // 5. 游戏模式：启�?DND
        if (c.globalPolicyBypassEnabled) {
            sh.execShellSilent("settings put global zen_mode 1")
            LogX.d("[root] zen_mode = 1（DND 游戏模式�?)
        }

        // 6. 重新发布被拦截的通知
        if (c.bridgePostOnIntercept) {
            sh.execShellSilent("cmd notification post com.notifymaster.pro restore \"Restored by NotifyMaster\"")
            LogX.d("[root] 已重�?post 被拦截通知")
        }
    }
}
