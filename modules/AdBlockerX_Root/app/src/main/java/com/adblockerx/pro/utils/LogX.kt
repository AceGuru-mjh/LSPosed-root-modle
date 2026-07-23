package com.adblockerx.pro.utils

import android.util.Log

/**
 * 缁熶竴鏃ュ織宸ュ叿
 * TAG: AdBlockerX-Pro锛屽彲鐢?adb logcat -s AdBlockerX:V 鏌ョ湅
 */
object LogX {
    private const val TAG = "AdBlockerX-Pro"
    var debugEnabled = true

    fun d(msg: String) {
        if (debugEnabled) {
            Log.d(TAG, msg)
            try { de.robv.android.xposed.XposedBridge.log("[$TAG:DEBUG] $msg") } catch (_: Throwable) {}
        }
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG] $msg") } catch (_: Throwable) {}
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:WARN] $msg") } catch (_: Throwable) {}
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:ERROR] $msg") } catch (_: Throwable) {}
    }

    fun e(msg: String, t: Throwable) {
        Log.e(TAG, msg, t)
        try { de.robv.android.xposed.XposedBridge.log("[$TAG:ERROR] $msg\n${t.stackTraceToString()}") } catch (_: Throwable) {}
    }

    fun hookSuccess(cls: String, method: String) {
        d("[Hook OK] $cls.$method")
    }

    fun hookFailed(cls: String, method: String, t: Throwable? = null) {
        w("[Hook FAIL] $cls.$method : ${t?.message ?: "unknown"}")
    }
}
