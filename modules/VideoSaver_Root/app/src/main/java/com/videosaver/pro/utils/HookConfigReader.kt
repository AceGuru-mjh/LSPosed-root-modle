package com.videosaver.pro.utils

import com.google.gson.Gson
import com.videosaver.pro.models.VideoConfig

/**
 * Hook 侧配置读取器（Root 版）
 *
 * 优先使用 XSharedPreferences 直接读取模块 prefs 文件（LSPosed 模式，跨进程）�?
 * 失败时回退�?Context-based ConfigManager（同进程）�?
 */
object HookConfigReader {

    private const val MODULE_PKG = "com.videosaver.pro"
    private val gson = Gson()

    fun readGlobal(): VideoConfig? {
        return try {
            val xsp = de.robv.android.xposed.XSharedPreferences(MODULE_PKG, ConfigManager.PREFS_NAME)
            xsp.makeWorldReadable()
            val json = xsp.getString("global_config", null) ?: return null
            gson.fromJson(json, VideoConfig::class.java)
        } catch (_: Throwable) { null }
    }
}
