package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogStore
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream

/**
 * WebView 广告拦截 Hook（Root 版同 NoRoot，应用层�?
 *
 * 拦截策略�?
 *  1. WebViewClient.shouldOverrideUrlLoading：拦截广告页跳转
 *  2. WebViewClient.shouldInterceptRequest：对广告 URL 返回 404 �?WebResourceResponse
 *  3. WebView.loadUrl：拦截广告页加载
 *  4. WebViewClient.onPageFinished：注�?CSS/JS 隐藏常见广告元素（可选）
 */
object WebViewAdHook {

    private val HIDE_AD_JS = """
        (function() {
            try {
                var selectors = [
                    '[id*="ad" i]', '[class*="ad" i]', '[id*="banner" i]',
                    'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
                    'ins.adsbygoogle', 'div[class*="banner"]', 'div[id*="sponsor"]'
                ];
                var css = selectors.join(',') + ' { display:none !important; visibility:hidden !important; height:0 !important; width:0 !important; }';
                var style = document.createElement('style');
                style.type = 'text/css';
                style.appendChild(document.createTextNode(css));
                document.head.appendChild(style);
            } catch(e) {}
        })();
    """.trimIndent()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.webviewAdEnabled) return
        LogX.i("WebViewAdHook 启动（应用进程内�?)

        hookX5WebView(lpparam, cfg)
        hookShouldOverrideUrlLoading(lpparam)
        hookShouldInterceptRequest(lpparam)
        hookLoadUrl(lpparam)
        if (cfg.injectJsEnabled) hookOnPageFinished(lpparam)
    }

    private fun hookX5WebView(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        val x5Exists = try {
            XposedHelpers.findClassIfExists("com.tencent.smtt.sdk.WebView", lpparam.classLoader) != null
        } catch (_: Throwable) { false }

        if (!x5Exists) {
            LogX.d("[WebView] X5 WebView not found, skipping")
            return
        }
    }

    private fun hookShouldOverrideUrlLoading(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvcClass, "shouldOverrideUrlLoading",
                    "android.webkit.WebView", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(1) as? String ?: return
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截跳转: $url")
                                try { LogStore.add("blocked", "拦截广告: $url") } catch (_: Exception) { }
                                try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                                p.result = true
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                val wrrClass = XposedHelpers.findClassIfExists(
                    "android.webkit.WebResourceRequest", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookMethod(wvcClass, "shouldOverrideUrlLoading",
                    "android.webkit.WebView", wrrClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val req = p.args.getOrNull(1) ?: return
                            val url = try {
                                XposedHelpers.callMethod(req, "getUrl")?.toString()
                            } catch (_: Throwable) { null } ?: return
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截跳转: $url")
                                try { LogStore.add("blocked", "拦截广告: $url") } catch (_: Exception) { }
                                try { LogStore.incrementCounter(1) } catch (_: Exception) { }
                                p.result = true
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.shouldOverrideUrlLoading 异常", e)
        }
    }

    private fun hookShouldInterceptRequest(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return
            val wrrClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebResourceRequest", lpparam.classLoader) ?: return
            val wrrRespClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebResourceResponse", lpparam.classLoader) ?: return

            XposedHelpers.findAndHookMethod(wvcClass, "shouldInterceptRequest",
                "android.webkit.WebView", wrrClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val req = p.args.getOrNull(1) ?: return
                        val url = try {
                            XposedHelpers.callMethod(req, "getUrl")?.toString()
                        } catch (_: Throwable) { null } ?: return
                        if (!HostsFilterHook.isUrlBlocked(url)) return
                        try {
                            val empty = ByteArrayInputStream(ByteArray(0))
                            val resp = wrrRespClass.getConstructor(
                                String::class.java,
                                String::class.java,
                                java.io.InputStream::class.java
                            ).newInstance("text/plain", "utf-8", empty)
                            XposedHelpers.callMethod(resp, "setStatusCode", 404)
                            try { XposedHelpers.callMethod(resp, "setReasonPhrase", "Not Found") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                            LogX.i("[WebView] 拦截请求 404: $url")
                            p.result = resp
                        } catch (e: Throwable) {
                            LogX.e("[WebView] 构�?404 响应异常: $url", e)
                        }
                    }
                })
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.shouldInterceptRequest 异常", e)
        }
    }

    private fun hookLoadUrl(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(0) as? String ?: return
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截 loadUrl: $url")
                                p.result = null
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java, MutableMap::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(0) as? String ?: return
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截 loadUrl(Map): $url")
                                p.result = null
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadDataWithBaseURL",
                    String::class.java, String::class.java,
                    String::class.java, String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val baseUrl = p.args.getOrNull(0) as? String ?: return
                            if (HostsFilterHook.isUrlBlocked(baseUrl)) {
                                LogX.i("[WebView] 拦截 loadDataWithBaseURL: $baseUrl")
                                p.result = null
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.loadUrl 异常", e)
        }
    }

    private fun hookOnPageFinished(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(wvcClass, "onPageFinished",
                "android.webkit.WebView", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val view = p.args.getOrNull(0) ?: return
                        try {
                            XposedHelpers.callMethod(view, "evaluateJavascript",
                                HIDE_AD_JS, null as Any?)
                            LogX.d("[WebView] 已注入广告隐�?JS")
                        } catch (e: Throwable) {
                            LogX.d("[WebView] 注入 JS 失败: ${e.message}")
                        }
                    }
                })
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.onPageFinished 异常", e)
        }
    }
}
