package com.videosaver.pro.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.videosaver.pro.models.VideoConfig

/**
 * 配置管理器（Root 版）
 *
 * 双通道读取�?
 *  1. UI 侧（模块进程）：通过 SharedPreferences 读写
 *  2. Hook 侧（目标APP进程）：通过 XSharedPreferences 读取模块 prefs（LSPosed 模式�?
 *     或通过 Context.getSharedPreferences 读取（同进程�?
 *
 * LSPosed 兼容：prefs 使用 MODE_WORLD_READABLE（LSPosed 拦截并放行），失败回退 MODE_PRIVATE�?
 */
object ConfigManager {

    const val PREFS_NAME = "video_saver_pro_prefs"
    private const val KEY_ALL = "all_app_configs"
    private const val KEY_GLOBAL = "global_config"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isInitialized(): Boolean = prefs != null

    fun getGlobalConfig(): VideoConfig {
        val def = VideoConfig(packageName = "global")
        if (!isInitialized()) return def
        val json = prefs?.getString(KEY_GLOBAL, null) ?: return def
        return try { gson.fromJson(json, VideoConfig::class.java) ?: def } catch (_: Throwable) { def }
    }

    fun saveGlobalConfig(cfg: VideoConfig) {
        if (!isInitialized()) return
        cfg.lastModified = System.currentTimeMillis()
        prefs?.edit()?.putString(KEY_GLOBAL, gson.toJson(cfg))?.apply()
    }

    fun getAllConfigs(): MutableMap<String, VideoConfig> {
        if (!isInitialized()) return mutableMapOf()
        val json = prefs?.getString(KEY_ALL, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, VideoConfig>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (_: Throwable) { mutableMapOf() }
    }

    fun getConfig(pkg: String): VideoConfig {
        return getAllConfigs()[pkg] ?: getGlobalConfig()
    }

    fun saveConfig(cfg: VideoConfig) {
        val all = getAllConfigs()
        cfg.lastModified = System.currentTimeMillis()
        all[cfg.packageName] = cfg
        prefs?.edit()?.putString(KEY_ALL, gson.toJson(all))?.apply()
    }

    fun resetAll() { prefs?.edit()?.clear()?.apply() }

    /** 导出全部配置�?JSON 字符�?*/
    fun exportConfig(): String {
        val data = mutableMapOf<String, Any?>()
        try {
            prefs?.all?.forEach { (k, v) -> data[k] = v }
        } catch (_: Throwable) { }
        return gson.toJson(data)
    }

    /** �?JSON 字符串导入配置，返回是否成功 */
    fun importConfig(json: String): Boolean {
        return try {
            val data: Map<String, Any?> = gson.fromJson(json, object : TypeToken<Map<String, Any?>>() {}.type)
                ?: return false
            val editor = prefs?.edit()?.clear() ?: return false
            data.forEach { (k, v) ->
                try {
                    when (v) {
                        is String -> editor.putString(k, v)
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v)
                        is Double -> editor.putFloat(k, v.toFloat())
                        is Number -> editor.putFloat(k, v.toFloat())
                        is Set<*> -> @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(k, v.filterIsInstance<String>().toSet())
                        else -> null
                    }
                } catch (_: Throwable) { null }
            }
            editor.apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}
