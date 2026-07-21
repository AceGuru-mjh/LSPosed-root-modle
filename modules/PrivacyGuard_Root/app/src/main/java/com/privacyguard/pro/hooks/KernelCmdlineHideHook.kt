package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.FakeDeviceCache
import com.privacyguard.pro.utils.InstanceTagger
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】内�?cmdline 隐藏Hook（Root 专属�?
 *
 * 功能�?
 *  - Hook 文件读取 /proc/cmdline 返回混淆内容
 *  - 干扰 APP 通过 cmdline 进行 Root/调试环境检�?
 *
 * 应用场景�?
 *  - 部分 APP 通过读取 /proc/cmdline 检�?Magisk、custom ROM、调试参�?
 *  - 伪造为标准 Qualcomm 设备 cmdline
 *
 * 硬性限制：
 *  - 仅修�?Java 层读�?/proc/cmdline 的返回�?
 *  - Native 层直�?open/read 系统调用的检测无法拦�?
 *  - Hook File/RandomAccessFile 构造函数对性能有轻微影�?
 */
object KernelCmdlineHideHook {

    /** 伪造的 /proc/cmdline 内容（标�?Qualcomm 设备�?*/
    private val FAKE_CMDLINE = FakeDeviceCache.fakeKernelCmdline

    /** /proc/cmdline 路径 */
    private const val CMDLINE_PATH = "/proc/cmdline"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.kernelCmdlineHideEnabled) return
        LogX.i("【实验性】内�?cmdline 隐藏启动（Root 专属�?)

        hookFileInputStreamForCmdline(lpparam)
        hookRandomAccessFileForCmdline(lpparam)
        applyShizukuMount(lpparam, cfg)
    }

    fun applyShizukuMount(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.kernelCmdlineMountEnabled) return
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过内核cmdline挂载")
            return
        }
        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        ShizukuHelper.execShellSilent("cp /proc/cmdline /data/local/tmp/cmdline.bak")
                        ShizukuHelper.execShellSilent("sed 's/androidboot.verifiedbootstate=orange/androidboot.verifiedbootstate=green/g' /proc/cmdline > /data/local/tmp/fake_cmdline")
                        ShizukuHelper.execShellSilent("mount --bind /data/local/tmp/fake_cmdline /proc/cmdline")
                        LogX.i("Shizuku内核cmdline挂载完成")
                    } catch (e: Throwable) {
                        LogX.w("Shizuku内核cmdline挂载异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->ShizukuKernelCmdlineMount")
    }

    /**
     * Hook FileInputStream 读取 /proc/cmdline
     */
    private fun hookFileInputStreamForCmdline(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val fisCls = XposedHelpers.findClassIfExists(
                "java.io.FileInputStream", lpparam.classLoader) ?: return

            val fakeBytes = FAKE_CMDLINE.toByteArray()

            // FileInputStream(String path)
            try {
                XposedHelpers.findAndHookConstructor(fisCls, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val path = p.args[0] as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                    LogX.d("检测到 APP 读取 /proc/cmdline")
                                }
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("FileInputStream", "<init>(String) cmdline")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // FileInputStream(File file)
            try {
                val fileCls = XposedHelpers.findClassIfExists(
                    "java.io.File", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookConstructor(fisCls, fileCls,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val file = p.args[0] ?: return
                                val path = XposedHelpers.callMethod(file, "getAbsolutePath") as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                    LogX.d("检测到 APP 通过 File 读取 /proc/cmdline")
                                }
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("FileInputStream", "<init>(File) cmdline")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // Hook read(byte[], int, int)
            try {
                XposedHelpers.findAndHookMethod(fisCls, "read",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isCmdline") as? Boolean ?: return
                                if (!flag) return
                                val buf = p.args[0] as? ByteArray ?: return
                                val off = p.args[1] as Int
                                val len = p.args[2] as Int
                                val n = minOf(fakeBytes.size, len)
                                System.arraycopy(fakeBytes, 0, buf, off, n)
                                p.result = n
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("FileInputStream", "read(buf) cmdline")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("FileInputStream", "cmdline", e)
        }
    }

    /**
     * Hook RandomAccessFile 读取 /proc/cmdline
     */
    private fun hookRandomAccessFileForCmdline(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val rafCls = XposedHelpers.findClassIfExists(
                "java.io.RandomAccessFile", lpparam.classLoader) ?: return

            val fakeBytes = FAKE_CMDLINE.toByteArray()

            // RandomAccessFile(String path, String mode)
            try {
                XposedHelpers.findAndHookConstructor(rafCls, String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val path = p.args[0] as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                    LogX.d("检测到 APP 通过 RandomAccessFile 读取 /proc/cmdline")
                                }
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("RandomAccessFile", "<init>(String, mode) cmdline")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // RandomAccessFile(File file, String mode)
            try {
                val fileCls = XposedHelpers.findClassIfExists(
                    "java.io.File", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookConstructor(rafCls, fileCls, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val file = p.args[0] ?: return
                                val path = XposedHelpers.callMethod(file, "getAbsolutePath") as? String ?: return
                                if (path == CMDLINE_PATH) {
                                    InstanceTagger.setTag(p.thisObject, "isCmdline", true)
                                }
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("RandomAccessFile", "<init>(File, mode) cmdline")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // Hook readLine()
            try {
                XposedHelpers.findAndHookMethod(rafCls, "readLine",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isCmdline") as? Boolean ?: return
                                if (flag) {
                                    p.result = FAKE_CMDLINE
                                }
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("RandomAccessFile", "readLine cmdline")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // Hook read(byte[], int, int)
            try {
                XposedHelpers.findAndHookMethod(rafCls, "read",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val flag = InstanceTagger.getTag(p.thisObject, "isCmdline") as? Boolean ?: return
                                if (!flag) return
                                val buf = p.args[0] as? ByteArray ?: return
                                val off = p.args[1] as Int
                                val len = p.args[2] as Int
                                val n = minOf(fakeBytes.size, len)
                                System.arraycopy(fakeBytes, 0, buf, off, n)
                                p.result = n
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("RandomAccessFile", "read(buf) cmdline")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("RandomAccessFile", "cmdline", e)
        }
    }
}
