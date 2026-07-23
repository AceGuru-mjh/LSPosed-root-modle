package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogStore
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * OkHttp 广告拦截 Hook（Root 版同 NoRoot??
 *
 * 拦截 RealCall.execute / enqueue + Interceptor.Chain.proceed，多候选类名容??
 */
object OkHttpAdHook {

    private val REAL_CALL_CANDIDATES = listOf(
        "okhttp3.RealCall",
        "okhttp3.internal.connection.RealCall",
        "okhttp3.OkHttpClient\$RealCall"
    )

    private val RESPONSE_CLASS = "okhttp3.Response"
    private val RESPONSE_BUILDER_CLASS = "okhttp3.Response\$Builder"
    private val REQUEST_CLASS = "okhttp3.Request"
    private val HTTP_URL_CLASS = "okhttp3.HttpUrl"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.okHttpAdEnabled) return
        LogX.i("OkHttpAdHook 启动（应用进程内，多候选类名容错）")

        hookRealCall(lpparam)
        hookInterceptorChain(lpparam)
    }

    private fun hookRealCall(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (className in REAL_CALL_CANDIDATES) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue

            try {
                XposedHelpers.findAndHookMethod(clazz, "execute",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = try { extractRequestUrl(p.thisObject) } catch (_: Throwable) { null }
                            if (url != null && HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[OkHttp] 拦截 execute: $url")
                                try { LogStore.add("blocked", "拦截 OkHttp: $url") } catch (_: Exception) { }
                                try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                                p.result = buildEmptyResponse(lpparam, url)
                            }
                        }
                    })
                LogX.d("[OkHttp] ??Hook $className.execute")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(clazz, "enqueue",
                    "okhttp3.Callback",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = try { extractRequestUrl(p.thisObject) } catch (_: Throwable) { null }
                            if (url != null && HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[OkHttp] 拦截 enqueue: $url")
                                try { LogStore.add("blocked", "拦截 OkHttp: $url") } catch (_: Exception) { }
                                try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                                p.result = null
                            }
                        }
                    })
                LogX.d("[OkHttp] ??Hook $className.enqueue")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun hookInterceptorChain(lpparam: XC_LoadPackage.LoadPackageParam) {
        val chainClass = XposedHelpers.findClassIfExists(
            "okhttp3.Interceptor\$Chain", lpparam.classLoader) ?: return
        val requestClass = XposedHelpers.findClassIfExists(REQUEST_CLASS, lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(chainClass, "proceed",
                requestClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val req = p.args.getOrNull(0) ?: return
                        val url = try { extractUrlFromRequest(req) } catch (_: Throwable) { null }
                        if (url != null && HostsFilterHook.isUrlBlocked(url)) {
                            LogX.i("[OkHttp] 拦截 Chain.proceed: $url")
                            try { LogStore.add("blocked", "拦截 OkHttp: $url") } catch (_: Exception) { }
                            try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                            p.result = buildEmptyResponse(lpparam, url)
                        }
                    }
                })
            LogX.d("[OkHttp] ??Hook Interceptor.Chain.proceed")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun extractRequestUrl(realCall: Any): String? {
        return try {
            val req = XposedHelpers.callMethod(realCall, "request") ?: return null
            extractUrlFromRequest(req)
        } catch (_: Throwable) { null }
    }

    private fun extractUrlFromRequest(req: Any): String? {
        return try {
            val url = XposedHelpers.callMethod(req, "url") ?: return null
            url.toString()
        } catch (_: Throwable) {
            try { req.toString() } catch (_: Throwable) { null }
        }
    }

    private fun buildEmptyResponse(lpparam: XC_LoadPackage.LoadPackageParam, url: String): Any? {
        return try {
            val respClass = XposedHelpers.findClassIfExists(RESPONSE_CLASS, lpparam.classLoader) ?: return null
            val builderClass = XposedHelpers.findClassIfExists(RESPONSE_BUILDER_CLASS, lpparam.classLoader) ?: return null

            val builder = try {
                val b = XposedHelpers.newInstance(builderClass)
                try {
                    val requestClass = XposedHelpers.findClassIfExists(REQUEST_CLASS, lpparam.classLoader)
                    if (requestClass != null) {
                        val reqBuilder = XposedHelpers.callStaticMethod(requestClass, "newBuilder") ?: return@try
                        val fakeReq = try {
                            val httpUrlClass = XposedHelpers.findClassIfExists(HTTP_URL_CLASS, lpparam.classLoader)
                            if (httpUrlClass != null) {
                                val urlObj = XposedHelpers.callStaticMethod(httpUrlClass, "get", "http://localhost")
                                XposedHelpers.callMethod(reqBuilder, "url", urlObj)
                            }
                            XposedHelpers.callMethod(reqBuilder, "build")
                        } catch (_: Throwable) { null }

                        if (fakeReq != null) {
                            try { XposedHelpers.callMethod(b, "request", fakeReq) } catch (_: NoSuchMethodError) { }
                        }
                    }
                } catch (_: Throwable) { }

                try { XposedHelpers.callMethod(b, "code", 404) } catch (_: Throwable) { }
                XposedHelpers.callMethod(b, "build")
            } catch (_: Throwable) { null }

            builder
        } catch (e: Throwable) {
            LogX.e("[OkHttp] buildEmptyResponse failed", e)
            null
        }
    }
}
