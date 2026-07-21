package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.Socket

/**
 * 网络延迟优化 Hook（实验性）
 *
 * 功能�?
 *  - Hook Socket 构造函数，对所有新�?TCP 连接自动设置 TCP_NODELAY=1
 *  - Hook Socket.setReceiveBufferSize / setSendBufferSize 强制扩大缓冲�?
 *  - Hook Socket.setTcpNoDelay 强制�?true（取�?Nagle 算法�?
 *
 * 硬性限制：
 *  - 仅修改应用进程内�?Socket 配置
 *  - 实际网络延迟由物理链路和运营商路由决�?
 */
object NetworkLatencyOptHook {

    private const val RECV_BUFFER = 256 * 1024
    private const val SEND_BUFFER = 256 * 1024

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.networkLatencyOptEnabled) return
        LogX.i("网络延迟优化启动（实验性）")

        hookSocketConstructors(lpparam)
        hookSocketOptions(lpparam)
        hookDatagramSocket(lpparam)
    }

    private fun hookSocketConstructors(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sk = XposedHelpers.findClassIfExists("java.net.Socket", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookConstructor(sk, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        applyTcpNoDelay(p.thisObject as? Socket)
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookConstructor(sk,
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            applyTcpNoDelay(p.thisObject as? Socket)
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookConstructor(sk,
                    "java.net.InetAddress", Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            applyTcpNoDelay(p.thisObject as? Socket)
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("Socket", "constructors -> TCP_NODELAY")
        } catch (e: Throwable) {
            LogX.hookFailed("Socket", "constructors", e)
        }
    }

    private fun hookSocketOptions(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sk = XposedHelpers.findClassIfExists("java.net.Socket", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(sk, "setTcpNoDelay",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = true
                        }
                    })
                LogX.hookSuccess("Socket", "setTcpNoDelay -> true")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(sk, "setReceiveBufferSize",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = p.args[0] as Int
                            if (v < RECV_BUFFER) p.args[0] = RECV_BUFFER
                        }
                    })
                LogX.hookSuccess("Socket", "setReceiveBufferSize -> >= $RECV_BUFFER")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(sk, "setSendBufferSize",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = p.args[0] as Int
                            if (v < SEND_BUFFER) p.args[0] = SEND_BUFFER
                        }
                    })
                LogX.hookSuccess("Socket", "setSendBufferSize -> >= $SEND_BUFFER")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Socket", "setSocketOptions", e)
        }
    }

    private fun applyTcpNoDelay(socket: Socket?) {
        try {
            socket ?: return
            if (!socket.isConnected) return
            socket.tcpNoDelay = true
            try { socket.receiveBufferSize = RECV_BUFFER } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            try { socket.sendBufferSize = SEND_BUFFER } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookDatagramSocket(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ds = XposedHelpers.findClassIfExists(
                "java.net.DatagramSocket", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(ds, "setReceiveBufferSize",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = p.args[0] as Int
                            if (v < RECV_BUFFER) p.args[0] = RECV_BUFFER
                        }
                    })
                LogX.hookSuccess("DatagramSocket", "setReceiveBufferSize")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("DatagramSocket", "setReceiveBufferSize", e)
        }
    }
}
