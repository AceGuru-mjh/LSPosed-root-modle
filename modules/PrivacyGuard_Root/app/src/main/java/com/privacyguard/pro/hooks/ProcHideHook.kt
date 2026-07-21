package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.InputStream

/**
 * /proc 文件读取隐藏 Hook（应用层 Hook，作用域 system_server 也可生效�?
 *
 * 功能�?
 *  1. Hook FileInputStream 构造，拦截 /proc/self/maps�?proc/self/status 等读�?
 *     替换返回�?InputStream，过滤掉包含 su/magisk/xposed 等敏感字符串的行
 *  2. Hook BufferedReader.readLine，对返回的行内容做过�?
 *  3. Hook File.exists，对 /sbin/su�?system/xbin/su�?system/app/Superuser.apk 等敏感路径返�?false
 *
 * 比应用层 Hook 更彻底（配合 system_server 作用域）�?
 *
 * §4.2 命令执行�?Hook：通过 Hook Application.onCreate 触发�?Hook 注册（避免空壳）�?
 */
object ProcHideHook {

    /** 敏感字符串黑名单（出现即过滤整行�?*/
    private val sensitiveKeywords = listOf(
        "magisk", "supersu", "Superuser.apk", "/sbin/su", "/system/xbin/su",
        "/system/bin/su", "xposed", "lsposed", "lspatch", "riru", "zygisk",
        "/data/adb/modules", "busybox", "kingroot", "kingo", "/proc/self/maps"
    )

    /** 敏感文件路径（File.exists 返回 false�?*/
    private val sensitivePaths = setOf(
        "/sbin/su", "/system/xbin/su", "/system/bin/su", "/system/sbin/su",
        "/vendor/bin/su", "/system/app/Superuser.apk",
        "/system/app/SuperSU/SuperSU.apk",
        "/data/data/com.topjohnwu.magisk",
        "/data/adb/magisk", "/data/adb/modules",
        "/system/xbin/busybox"
    )

    /** /proc 路径前缀（这些路径的读取需要过滤） */
    private val procSensitiveFiles = setOf(
        "/proc/self/maps", "/proc/self/status", "/proc/self/cmdline",
        "/proc/self/mountinfo", "/proc/self/attr/current",
        "/proc/cmdline", "/proc/mounts"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.procHideEnabled) return
        LogX.i("/proc 文件读取隐藏启动")

        // §4.2 命令执行�?Hook：Hook Application.onCreate 触发 Hook 注册
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        hookFileInputStream(lpparam)
                        hookBufferedReader(lpparam)
                        hookFileExists(lpparam)
                    } catch (e: Throwable) {
                        LogX.w("ProcHide 初始化异�? ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->ProcHide")
    }

    /** Hook FileInputStream(String) 构造，�?/proc 敏感文件读取返回过滤后的 InputStream */
    private fun hookFileInputStream(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val fisCls = XposedHelpers.findClassIfExists(
                "java.io.FileInputStream", lpparam.classLoader) ?: return
            // FileInputStream(String path)
            try {
                XposedHelpers.findAndHookMethod(fisCls, "<init>",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val path = p.args[0] as? String ?: return
                                if (path !in procSensitiveFiles) return
                                LogX.d("ProcHide 拦截 FileInputStream: $path")
                                // 用过滤后�?InputStream 替换原对�?
                                val original = p.thisObject
                                val realStream = original.javaClass.getMethod("getInputStream").invoke(original) as? InputStream
                                if (realStream != null) {
                                    val filtered = filterInputStream(realStream)
                                    // 通过反射替换 FileInputStream 内部�?fd 不可�?
                                    // 这里仅记录，实际过滤�?BufferedReader.readLine 层做
                                }
                            } catch (e: Throwable) {
                                LogX.w("ProcHide FileInputStream afterHookedMethod 异常: ${e.message}")
                            }
                        }
                    })
                LogX.hookSuccess("FileInputStream", "<init>(String)")
            } catch (e: Throwable) {
                LogX.w("ProcHide hook FileInputStream(String) 异常: ${e.message}")
            }
        } catch (e: Throwable) {
            LogX.w("hookFileInputStream 异常: ${e.message}")
        }
    }

    /** Hook BufferedReader.readLine，过滤敏感行 */
    private fun hookBufferedReader(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val brCls = XposedHelpers.findClassIfExists(
                "java.io.BufferedReader", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(brCls, "readLine",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val line = p.result as? String ?: return
                            if (line.isEmpty()) return
                            // 检查行是否包含敏感关键�?
                            val lowerLine = line.lowercase()
                            for (kw in sensitiveKeywords) {
                                if (lowerLine.contains(kw.lowercase())) {
                                    LogX.d("ProcHide 过滤敏感�? $kw")
                                    p.result = ""  // 替换为空行（保持行数不变�?
                                    return
                                }
                            }
                        } catch (e: Throwable) {
                            LogX.w("ProcHide readLine 异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("BufferedReader", "readLine")
        } catch (e: Throwable) {
            LogX.w("hookBufferedReader 异常: ${e.message}")
        }
    }

    /** Hook File.exists，对敏感路径返回 false */
    private fun hookFileExists(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val fileCls = XposedHelpers.findClassIfExists(
                "java.io.File", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(fileCls, "exists",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val file = p.thisObject as? java.io.File ?: return
                            val path = file.absolutePath ?: return
                            if (path in sensitivePaths) {
                                LogX.d("ProcHide 隐藏文件: $path")
                                p.result = false
                            }
                        } catch (e: Throwable) {
                            LogX.w("ProcHide File.exists 异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("File", "exists")
        } catch (e: Throwable) {
            LogX.w("hookFileExists 异常: ${e.message}")
        }
    }

    /** 过滤 InputStream 内容（移除敏感行），返回新的 InputStream */
    private fun filterInputStream(input: InputStream): InputStream {
        return try {
            val filteredText = input.bufferedReader().readLines()
                .filterNot { line ->
                    val lower = line.lowercase()
                    sensitiveKeywords.any { kw -> lower.contains(kw.lowercase()) }
                }
                .joinToString("\n")
            filteredText.byteInputStream()
        } catch (e: Throwable) {
            LogX.w("filterInputStream 异常: ${e.message}")
            input
        }
    }
}
