package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知历史 Hook（Root �?- 应用进程�?+ 可选全局捕获�?
 *
 * 功能：Hook NotificationManager.notify，记录通知到内存历史列表（提供查询接口）�?
 *
 * Root 版可配合 NotifyListenerServiceHook 捕获所�?APP 的通知�?
 */
object NotifyHistoryHook {

    data class HistoryEntry(
        val timestamp: Long,
        val timeStr: String,
        val packageName: String,
        val id: Int,
        val tag: String?,
        val title: String?,
        val text: String?,
        val ticker: String?
    )

    private val historyList = mutableListOf<HistoryEntry>()
    private const val MAX_HISTORY = 500
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notifyHistoryEnabled) return
        LogX.i("通知历史启动（内存记录，最�?$MAX_HISTORY 条）")

        hookNotify(lpparam)
    }

    private fun hookNotify(lpparam: XC_LoadPackage.LoadPackageParam) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = p.args[0] as Int
                        val notif = p.args[1] ?: return
                        recordEntry(lpparam.packageName, id, null, notif)
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id, Notification)[history]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id, Notification)[history]", e) }

        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val tag = p.args[0] as? String
                        val id = p.args[1] as Int
                        val notif = p.args[2] ?: return
                        recordEntry(lpparam.packageName, id, tag, notif)
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(tag, id, Notification)[history]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(tag, id, Notification)[history]", e) }
    }

    private fun recordEntry(pkg: String, id: Int, tag: String?, notif: Any) {
        try {
            val title = readExtrasCharSequence(notif, "android.title")
            val text = readExtrasCharSequence(notif, "android.text")
            val ticker = try {
                XposedHelpers.callMethod(notif, "getTickerText")?.toString()
            } catch (_: Throwable) { null }

            val entry = HistoryEntry(
                timestamp = System.currentTimeMillis(),
                timeStr = dateFormat.format(Date()),
                packageName = pkg,
                id = id,
                tag = tag,
                title = title,
                text = text,
                ticker = ticker
            )
            synchronized(historyList) {
                historyList.add(0, entry)
                if (historyList.size > MAX_HISTORY) {
                    historyList.subList(MAX_HISTORY, historyList.size).clear()
                }
            }
            LogX.d("通知历史已记�? pkg=$pkg id=$id title=$title")
        } catch (e: Throwable) {
            LogX.w("通知历史记录异常: ${e.message}")
        }
    }

    private fun readExtrasCharSequence(notif: Any, key: String): String? {
        return try {
            val extras = XposedHelpers.callMethod(notif, "getExtras") ?: return null
            (XposedHelpers.callMethod(extras, "getCharSequence", key) as? CharSequence)?.toString()
        } catch (_: Throwable) { null }
    }

    // ===== 查询接口 =====

    @Suppress("unused")
    fun getAllHistory(): List<HistoryEntry> = synchronized(historyList) { historyList.toList() }

    @Suppress("unused")
    fun searchHistory(keyword: String): List<HistoryEntry> {
        if (keyword.isBlank()) return getAllHistory()
        return synchronized(historyList) {
            historyList.filter {
                (it.title?.contains(keyword) == true) ||
                (it.text?.contains(keyword) == true) ||
                (it.ticker?.contains(keyword) == true)
            }.toList()
        }
    }

    @Suppress("unused")
    fun clearHistory() {
        synchronized(historyList) { historyList.clear() }
    }

    fun release() {
        synchronized(historyList) { historyList.clear() }
    }
}
