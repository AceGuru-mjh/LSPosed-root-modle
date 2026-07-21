package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内存整理 Hook（实验性）
 *
 * 功能�?
 *  - Hook Debug.MemoryInfo 读取，让游戏看到更优的内存状�?
 *  - Hook ActivityManager.getMemoryInfo 优化返回的内存压力指�?
 *  - Hook 低内存回�?onLowMemory / onTrimLevel，避免游戏主动降低画�?
 *  - 启动时主动调�?System.gc 提示 JVM 整理�?
 */
object MemoryDefragHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.memoryDefragEnabled) return
        LogX.i("内存整理启动（实验性）")

        hookDebugMemoryInfo(lpparam)
        hookActivityManagerMemoryInfo(lpparam)
        hookTrimMemory(lpparam)
        hintGc()
    }

    private fun hookDebugMemoryInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val dmi = XposedHelpers.findClassIfExists(
                "android.os.Debug.MemoryInfo", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(dmi, "getTotalPrivateDirty",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = 0
                        }
                    })
                LogX.hookSuccess("Debug.MemoryInfo", "getTotalPrivateDirty -> 0")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Debug.MemoryInfo", "getTotalPrivateDirty", e)
        }
    }

    private fun hookActivityManagerMemoryInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val am = XposedHelpers.findClassIfExists(
                "android.app.ActivityManager", lpparam.classLoader) ?: return
            val miClass = XposedHelpers.findClassIfExists(
                "android.app.ActivityManager.MemoryInfo", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(am, "getMemoryInfo", miClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val mi = p.args[0] ?: return
                                mi.javaClass.getField("availMem").setLong(mi, 2L * 1024 * 1024 * 1024)
                                mi.javaClass.getField("threshold").setLong(mi, 512L * 1024 * 1024)
                                mi.javaClass.getField("lowMemory").setBoolean(mi, false)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("ActivityManager", "getMemoryInfo -> availMem=2GB")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("ActivityManager", "getMemoryInfo", e)
        }
    }

    private fun hookTrimMemory(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val app = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(app, "onTrimMemory",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = 20
                        }
                    })
                LogX.hookSuccess("Application", "onTrimMemory -> UI_HIDDEN")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(app, "onLowMemory",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = null
                        }
                    })
                LogX.hookSuccess("Application", "onLowMemory -> skip")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onTrimMemory", e)
        }
    }

    private fun hintGc() {
        try {
            System.gc()
            System.runFinalization()
            LogX.d("主动 System.gc 提示已发�?)
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
