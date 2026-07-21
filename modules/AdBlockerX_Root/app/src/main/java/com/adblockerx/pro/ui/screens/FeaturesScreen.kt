package com.adblockerx.pro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.ui.components.FeatureCard
import com.adblockerx.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: AdBlockConfig, onConfigChange: (AdBlockConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("应用层基础拦截", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "WebView 广告拦截",
            "shouldOverrideUrlLoading / shouldInterceptRequest 404 / loadUrl 拦截 / 注入 JS",
            cfg.webviewAdEnabled,
            { val nc = cfg.copy(webviewAdEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "OkHttp 请求拦截",
            "RealCall.execute/enqueue + Interceptor.Chain.proceed 多候选类名容�?,
            cfg.okHttpAdEnabled,
            { val nc = cfg.copy(okHttpAdEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "URLConnection 拦截",
            "URL.openConnection �?IOException / HttpURLConnection 返回 404 / Https 同理",
            cfg.urlConnectionAdEnabled,
            { val nc = cfg.copy(urlConnectionAdEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内存 Hosts 黑名�?,
            "内置广告域名黑名�?+ 用户自定义，子域�?包含匹配",
            cfg.hostsFilterEnabled,
            { val nc = cfg.copy(hostsFilterEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "广告 SDK View 隐藏",
            "Hook 21 个广�?SDK �?View 类，构造后强制 GONE + 拦截 VISIBLE",
            cfg.adViewHideEnabled,
            { val nc = cfg.copy(adViewHideEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("应用层实验性拦�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "追踪 SDK 拦截",
            "Hook Umeng/TalkingData/Flurry/Bugly/BaiduMtj 等上报方法直�?return",
            cfg.trackerBlockEnabled,
            { val nc = cfg.copy(trackerBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Cookie 清理",
            "Hook CookieManager.getCookie 返回前过�?_ga/_gid/IDE 等追�?Cookie",
            cfg.cookieCleanEnabled,
            { val nc = cfg.copy(cookieCleanEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "重定向拦�?,
            "Hook WebViewClient.shouldOverrideUrlLoading 拦截广告跳转深链 / click 关键�?,
            cfg.redirectBlockEnabled,
            { val nc = cfg.copy(redirectBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Intent 拦截",
            "Hook startActivity / startActivityForResult 拦截广告 Intent 跳转",
            cfg.intentInterceptorEnabled,
            { val nc = cfg.copy(intentInterceptorEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 系统级拦截（需 Shizuku�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统 Hosts 修改",
            "Shizuku �?/data/adb/modules/adblockerx/system/etc/hosts（Magisk overlay�? mount --bind",
            cfg.systemHostsEnabled,
            { val nc = cfg.copy(systemHostsEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Private DNS 设置",
            "Shizuku settings put global private_dns_mode hostname + private_dns_specifier",
            cfg.privateDnsEnabled,
            { val nc = cfg.copy(privateDnsEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        if (cfg.privateDnsEnabled) {
            OutlinedTextField(
                value = cfg.privateDnsHost,
                onValueChange = { val nc = cfg.copy(privateDnsHost = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
                label = { Text("Private DNS 主机�?) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
        }

        FeatureCard(
            "DNS 解析 Hook",
            "Hook InetAddress/Network/Libcore.os 对广告域名返�?127.0.0.1",
            cfg.dnsResolverHookEnabled,
            { val nc = cfg.copy(dnsResolverHookEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 桥接",
            "Shizuku ndc resolver flushdefaultif 刷新系统 DNS 缓存",
            cfg.shizukuBridgeEnabled,
            { val nc = cfg.copy(shizukuBridgeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 实验性拦�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "iptables 网络层拦�?,
            "Shizuku iptables -A OUTPUT -d <ad_ip> -j DROP（前 50 个域名）",
            cfg.iptablesBlockEnabled,
            { val nc = cfg.copy(iptablesBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "本地 VPN 拦截",
            "Hook VpnService.Builder.establish 阻止 APP 自建 VPN 绕过拦截",
            cfg.vpnBasedBlockEnabled,
            { val nc = cfg.copy(vpnBasedBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        Text("系统级增强（Task24 新增�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统 DNS 缓存刷新",
            "ndc resolver flushdefaultif + settings put global private_dns_specifier，周期触�?,
            cfg.dnsCacheFlushEnabled,
            { val nc = cfg.copy(dnsCacheFlushEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        Text("高级选项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FeatureCard(
            "WebView 注入 JS",
            "onPageFinished 后注�?CSS 隐藏广告 DOM（可能影响页面正常显示）",
            cfg.injectJsEnabled,
            { val nc = cfg.copy(injectJsEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))
        FeatureCard(
            "内置广告黑名�?,
            "启用内置 90 条广告域名（关闭后仅匹配自定义黑名单�?,
            cfg.builtinBlocklistEnabled,
            { val nc = cfg.copy(builtinBlocklistEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(40.dp))
    }
}
