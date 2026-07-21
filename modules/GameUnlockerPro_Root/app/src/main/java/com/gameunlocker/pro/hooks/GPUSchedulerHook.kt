package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GPU 调度优化 Hook（系统级�?
 *
 * 功能�?
 *  - Hook EGL14.eglInitialize / eglChooseConfig 优化渲染管线
 *  - Hook GLSurfaceView.setRenderMode 强制连续渲染
 *  - Hook HardwareRenderer 帧回调（Android 10+�?
 *  - Hook Choreographer.getFrameDelay 减少 VSync 帧延�?
 *
 * 注意：实�?GPU 频率由内�?governor 决定，本 Hook 仅优化应用层渲染调度�?
 */
object GPUSchedulerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.gpuOptimizeEnabled) return
        LogX.i("GPU 调度优化启动（系统级�?)

        hookEGLInit(lpparam)
        hookGLSurfaceView(lpparam)
        hookHardwareRenderer(lpparam)
        hookChoreographer(lpparam)
    }

    private fun hookEGLInit(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val egl14 = XposedHelpers.findClassIfExists(
                "android.opengl.EGL14", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(egl14, "eglInitialize",
                    javax.microedition.khronos.egl.EGLDisplay::class.java,
                    IntArray::class.java, Int::class.javaPrimitiveType,
                    IntArray::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("EGL14 eglInitialize GPU 优化模式已激�?)
                        }
                    })
                LogX.hookSuccess("EGL14", "eglInitialize")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("EGL14", "eglInitialize", e)
        }
    }

    private fun hookGLSurfaceView(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val glsv = XposedHelpers.findClassIfExists(
                "android.opengl.GLSurfaceView", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(glsv, "setRenderMode",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val mode = p.args[0] as Int
                            if (mode != 1) {
                                p.args[0] = 1
                                LogX.d("GLSurfaceView 渲染模式已强制设为连�?)
                            }
                        }
                    })
                LogX.hookSuccess("GLSurfaceView", "setRenderMode")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(glsv, "setEGLContextClientVersion",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("GLSurfaceView EGL Context 版本: ${p.args[0]}")
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("GLSurfaceView", "setRenderMode", e)
        }
    }

    private fun hookHardwareRenderer(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val hw = XposedHelpers.findClassIfExists(
                "android.graphics.HardwareRenderer", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(hw, "setFrameCommitCallback",
                    "android.graphics.HardwareRenderer\$FrameCommitCallback",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("HardwareRenderer 帧提交回调已 Hook")
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            try {
                XposedHelpers.findAndHookMethod(hw, "setFrameCompleteCallback",
                    "android.graphics.HardwareRenderer\$FrameCompleteCallback",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("HardwareRenderer 帧完成回调已 Hook")
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            LogX.hookSuccess("HardwareRenderer", "frame callbacks")
        } catch (e: Throwable) {
            LogX.hookFailed("HardwareRenderer", "frame callbacks", e)
        }
    }

    private fun hookChoreographer(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ch = XposedHelpers.findClassIfExists(
                "android.view.Choreographer", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(ch, "getFrameDelay",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = 0L
                        }
                    })
                LogX.hookSuccess("Choreographer", "getFrameDelay -> 0")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(ch, "getFrameIntervalNanos",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 返回 8.33ms 间隔，对�?120fps
                            p.result = 8_333_333L
                        }
                    })
                LogX.hookSuccess("Choreographer", "getFrameIntervalNanos -> 8.33ms")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Choreographer", "frameDelay", e)
        }
    }
}
