package com.adblockerx.pro.utils

/**
 * 鍐呯疆骞垮憡鍩熷悕榛戝悕鍗曪紙Root 鐗堝悓 NoRoot锛岀害 90 鏉★級
 *
 * 璁捐鍘熷垯锛?
 *  - 鍐呭瓨涓淮鎶わ紝鍚屾椂渚?SystemHostsHook 鍐欏叆绯荤粺 hosts 鏂囦欢
 *  - 瀛愬煙鍚嶅尮閰嶏細host.endsWith(domain) 鎴?host.contains(domain)
 */
object AdBlockList {

    val BUILTIN_AD_DOMAINS: List<String> = listOf(
        // ===== Google / DoubleClick =====
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "adwords.google.com",
        "adsense.google.com",
        "googletagservices.com",
        "adsystem.google.com",
        "partnerad.l.doubleclick.net",

        // ===== 鑵捐 GDT / AMS =====
        "pgdt.ugdtimg.com",
        "t.gdt.qq.com",
        "e.qq.com",
        "ad.qq.com",
        "ams.qq.com",
        "gdt.qq.com",
        "qzonestyle.gtimg.cn",
        "mi.gdt.qq.com",

        // ===== 瀛楄妭璺冲姩 / 绌垮北鐢?/ 宸ㄩ噺寮曟搸 =====
        "ad.toutiao.com",
        "pdp.toutiao.com",
        "is.snssdk.com",
        "pg.snssdk.com",
        "ad.toutiao.com.cn",
        "adx.toutiao.com",
        "toblog.ctobsnssdk.com",
        "mssdk.bytedance.com",
        "sf3-fe-tos.pglstatp-tpl.pglstatp.com",
        "log.snssdk.com",
        "init.snssdk.com",

        // ===== 鐧惧害鑱旂洘 / Mobads =====
        "cpro.baidu.com",
        "mobads.baidu.com",
        "pos.baidu.com",
        "baidumobads.baidu.com",
        "als.baidu.com",
        "e.baidu.com",
        "hmma.baidu.com",
        "duclick.baidu.com",

        // ===== 闃块噷濡堝 / 娣樺疂鑱旂洘 =====
        "amdc.alibaba.com",
        "acs4baichuan.m.taobao.com",
        "adash-c.ut.taobao.com",
        "adashx.ut.taobao.com",
        "aenbaichuan.com",
        "baichuan.taobao.com",

        // ===== 蹇墜骞垮憡 =====
        "ad.kuaishou.com",
        "yt-adp.nsnssdk.com",
        "ssp.ksadx.com",

        // ===== 缃戞槗骞垮憡 =====
        "ad.bn.netease.com",
        "adwallet.netease.com",
        "g1.163.com",
        "adstest.163.com",

        // ===== 360 / 绁為┈ / 鎼滅嫍 =====
        "360.cn",
        "shuzilm.cn",
        "a.shenma.cn",
        "ideasad.com",

        // ===== Mobtech / Umeng / TalkingData =====
        "api.mob.com",
        "ad.talkingdata.com",
        "ulogs.umeng.com",
        "ulogs.umengcloud.com",
        "plbslog.umeng.com",
        "alogs.umeng.com",

        // ===== 鍏朵粬甯歌骞垮憡/杩借釜 =====
        "adsame.cn",
        "tanx.com",
        "sax.sina.cn",
        "sax.n.sina.com.cn",
        "r.dmp.cn",
        "admaster.com",
        "mediav.com",
        "miaozhen.com",
        "irs01.com",
        "adcdn.com",
        "moatads.com",
        "rubiconproject.com",
        "pubmatic.com",
        "criteo.com",
        "applovin.com",
        "chartboost.com",
        "inmobi.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "vungle.com",
        "tapjoy.com",
        "admob.com",
        "mm.adtech.com",
        "adtech.com",

        // ===== 杩借釜 SDK 鍩熷悕 =====
        "tracking.miui.com",
        "data.adsrvr.org",
        "pixel.facebook.com",
        "analytics.twitter.com",
        "snap.licdn.com",
        "px.ads.linkedin.com",
        "tags.tiqcdn.com",
        "collect.tencent.com",

        // ===== 缃戠洘/RTB 琛ュ厖 =====
        "adsymptotic.com",
        "yieldlab.net",
        "smartadserver.com",
        "openx.net",
        "3lift.com",
        "bidswitch.net",
        "contextweb.com",
        "quantserve.com",
        "scorecardresearch.com"
    )

    fun isBlocked(
        host: String?,
        builtinEnabled: Boolean,
        customList: List<String>
    ): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase().trim()

        if (builtinEnabled) {
            for (domain in BUILTIN_AD_DOMAINS) {
                val d = domain.lowercase()
                if (h == d || h.endsWith(".$d") || h.contains(d)) {
                    return true
                }
            }
        }

        for (domain in customList) {
            val d = domain.lowercase().trim()
            if (d.isBlank()) continue
            if (h == d || h.endsWith(".$d") || h.contains(d)) {
                return true
            }
        }
        return false
    }

    fun extractHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val u = url.trim()
            val start = when {
                u.startsWith("https://", true) -> 8
                u.startsWith("http://", true) -> 7
                else -> 0
            }
            val rest = u.substring(start)
            val end = rest.indexOfFirst { it == '/' || it == ':' || it == '?' || it == '#' }
            if (end < 0) rest else rest.substring(0, end)
        } catch (e: Exception) {
            null
        }
    }
}
