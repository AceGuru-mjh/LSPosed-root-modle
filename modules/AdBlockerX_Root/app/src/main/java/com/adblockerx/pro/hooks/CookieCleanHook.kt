package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Cookie 清理 Hook（Root 版同 NoRoot�?
 *
 * Hook CookieManager.getCookie 返回前过滤追踪类 Cookie
 */
object CookieCleanHook {

    private val TRACKING_COOKIE_KEYS = arrayOf(
        "_ga", "_gid", "_gat", "_gcl_au",
        "IDE", "uid", "trk", "track",
        "__qca", "_fbp", "fr",
        "tapad_tid", "tdid",
        "um_distinct_id", "umt",
        "Hm_lvt_", "Hm_lpvt_",
        "sajssdk_", "sensorsdata",
        "arr_affinity", "arr_session_id",
        "_drtid", "_clck", "_clsk",
        "ANON_ID", "ANON_ID_old"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.cookieCleanEnabled) return
        LogX.i("【实验性】CookieCleanHook 启动（应用进程内�?)

        hookCookieManagerGet(lpparam)
    }

    private fun hookCookieManagerGet(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cm = XposedHelpers.findClassIfExists(
                "android.webkit.CookieManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cm, "getCookie",
                    String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val raw = p.result as? String ?: return
                            val cleaned = filterTrackingCookies(raw)
                            if (cleaned != raw) {
                                p.result = cleaned
                                LogX.d("[Cookie] 清理追踪 Cookie: ${raw.length} -> ${cleaned.length}")
                            }
                        }
                    })
                LogX.hookSuccess("CookieManager", "getCookie(url)")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cm, "setCookie",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("[Cookie] APP 设置 Cookie: ${p.args.getOrNull(0)}")
                        }
                    })
                LogX.hookSuccess("CookieManager", "setCookie")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("CookieCleanHook 异常", e)
        }
    }

    private fun filterTrackingCookies(raw: String): String {
        val parts = raw.split(";")
        val kept = parts.filter { part ->
            val name = part.trim().substringBefore("=", "").trim()
            if (name.isEmpty()) return@filter true
            !TRACKING_COOKIE_KEYS.any { key ->
                name.equals(key, ignoreCase = true) || name.startsWith(key, ignoreCase = true)
            }
        }
        return kept.joinToString(";")
    }
}
