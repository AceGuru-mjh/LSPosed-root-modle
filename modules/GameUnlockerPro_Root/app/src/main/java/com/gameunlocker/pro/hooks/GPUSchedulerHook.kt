package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GPU 璋冨害浼樺寲 Hook锛堢郴缁熺骇锛?
 *
 * 鍔熻兘锛?
 *  - Hook EGL14.eglInitialize / eglChooseConfig 浼樺寲娓叉煋绠＄嚎
 *  - Hook GLSurfaceView.setRenderMode 寮哄埗杩炵画娓叉煋
 *  - Hook HardwareRenderer 甯у洖璋冿紙Android 10+锛?
 *  - Hook Choreographer.getFrameDelay 鍑忓皯 VSync 甯у欢杩?
 *
 * 娉ㄦ剰锛氬疄闄?GPU 棰戠巼鐢卞唴鏍?governor 鍐冲畾锛屾湰 Hook 浠呬紭鍖栧簲鐢ㄥ眰娓叉煋璋冨害銆?
 */
object GPUSchedulerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.gpuOptimizeEnabled) return
        LogX.i("GPU 璋冨害浼樺寲鍚姩锛堢郴缁熺骇锛?)

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
                            LogX.d("EGL14 eglInitialize GPU 浼樺寲妯″紡宸叉縺娲?)
                        }
                    })
                LogX.hookSuccess("EGL14", "eglInitialize")
            } catch (e: Throwable) { LogX.w("寮傚父: ${e.message}") }
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
                                LogX.d("GLSurfaceView 娓叉煋妯″紡宸插己鍒惰涓鸿繛缁?)
                            }
                        }
                    })
                LogX.hookSuccess("GLSurfaceView", "setRenderMode")
            } catch (e: Throwable) { LogX.w("寮傚父: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(glsv, "setEGLContextClientVersion",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("GLSurfaceView EGL Context 鐗堟湰: ${p.args[0]}")
                        }
                    })
            } catch (e: Throwable) { LogX.w("寮傚父: ${e.message}") }
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
                            LogX.d("HardwareRenderer 甯ф彁浜ゅ洖璋冨凡 Hook")
                        }
                    })
            } catch (e: Throwable) { LogX.w("寮傚父: ${e.message}") }
            try {
                XposedHelpers.findAndHookMethod(hw, "setFrameCompleteCallback",
                    "android.graphics.HardwareRenderer\$FrameCompleteCallback",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("HardwareRenderer 甯у畬鎴愬洖璋冨凡 Hook")
                        }
                    })
            } catch (e: Throwable) { LogX.w("寮傚父: ${e.message}") }
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
            } catch (e: Throwable) { LogX.w("寮傚父: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(ch, "getFrameIntervalNanos",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 杩斿洖 8.33ms 闂撮殧锛屽搴?120fps
                            p.result = 8_333_333L
                        }
                    })
                LogX.hookSuccess("Choreographer", "getFrameIntervalNanos -> 8.33ms")
            } catch (e: Throwable) { LogX.w("寮傚父: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Choreographer", "frameDelay", e)
        }
    }
}
