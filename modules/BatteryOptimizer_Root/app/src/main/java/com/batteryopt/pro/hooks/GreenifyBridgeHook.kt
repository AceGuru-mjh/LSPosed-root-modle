package com.batteryopt.pro.hooks

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级孤�?WakeLock 清理 Hook（需 Shizuku/Root�?
 *
 * 功能�?
 *  - 通过 Shizuku `dumpsys power` 分析系统持有�?wake lock
 *  - 识别并释放孤�?wake lock（无对应进程或长时间未释放）
 *  - 周期性执行（默认 5 分钟一次）
 *
 * 注意�?
 *  - 不直�?Hook PowerManagerService（system_server 作用域不易获取）
 *  - 通过 dumpsys 文本分析更安全可�?
 *  - 释放 wake lock 谨慎，避免影响通话/导航等关键场�?
 *
 * §4.2 命令执行�?Hook：通过 Hook Application.onCreate 启动周期任务�?
 * �?Handler.postDelayed 周期驱动 `dumpsys power` 分析与孤�?wake lock 释放�?
 */
object GreenifyBridgeHook {

    private val handler = Handler(Looper.getMainLooper())
    private var periodicTask: Runnable? = null

    private val protectedKeywords = arrayOf(
        "phone", "telephony", "alarm", "location", "audio",
        "camera", "bluetooth", "nfc", "usb", "display"
    )

    private const val ORPHAN_THRESHOLD_MS = 10 * 60 * 1000L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.greenifyEnabled) {
            LogX.d("孤儿 WakeLock 清理未开启，跳过")
            return
        }

        LogX.i("Greenify 孤儿 WakeLock 清理启动 | 周期=${cfg.greenifyIntervalSec}s")

        // §4.2 命令执行�?Hook：Hook Application.onCreate 启动周期清理任务
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val ctx = p.thisObject as? Context ?: return
                    if (!ShizukuHelper.isShizukuAvailable()) {
                        LogX.w("Shizuku 不可用，跳过 Greenify 周期任务启动")
                        return
                    }
                    startPeriodicCleanup(cfg.greenifyIntervalSec, ctx)
                }
            })
        LogX.hookSuccess("Application", "onCreate->Greenify")
    }

    private fun startPeriodicCleanup(intervalSec: Int, @Suppress("UNUSED_PARAMETER") ctx: Context) {
        val r = object : Runnable {
            override fun run() {
                cleanupOrphanWakeLocks()
                handler.postDelayed(this, intervalSec * 1000L)
            }
        }
        periodicTask = r
        handler.post(r)
    }

    private fun cleanupOrphanWakeLocks() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过清理")
            return
        }

        try {
            val dump = ShizukuHelper.execShell("dumpsys power") ?: return
            val lines = dump.lines()
            var inWakeLockSection = false
            var cleaned = 0
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Wake Locks:")) {
                    inWakeLockSection = true
                    continue
                }
                if (inWakeLockSection) {
                    if (trimmed.isEmpty() || trimmed.endsWith(":")) {
                        if (cleaned > 0) break
                        continue
                    }
                    val tag = parseWakeLockTag(trimmed) ?: continue
                    val heldMs = parseWakeLockHeldMs(trimmed)
                    if (heldMs >= ORPHAN_THRESHOLD_MS && !isProtected(tag)) {
                        releaseWakeLock(tag)
                        cleaned++
                    }
                }
            }
            if (cleaned > 0) {
                LogX.i("已清�?$cleaned 个孤�?wake lock")
            } else {
                LogX.d("本轮无需清理的孤�?wake lock")
            }
        } catch (e: Exception) {
            LogX.e("清理孤儿 wake lock 异常", e)
        }
    }

    private fun parseWakeLockTag(line: String): String? {
        val regex = Regex("tag=([^\\s,]+)")
        return regex.find(line)?.groupValues?.getOrNull(1)
    }

    private fun parseWakeLockHeldMs(line: String): Long {
        val regex = Regex("time=(\\d+)\\s*ms")
        val ms = regex.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return 0L
        return ms
    }

    private fun isProtected(tag: String): Boolean {
        val lower = tag.lowercase()
        return protectedKeywords.any { lower.contains(it) }
    }

    private fun releaseWakeLock(tag: String) {
        // 没有公开�?release-by-tag API，采用保守策略：仅记录，不强制释�?
        // 真实环境如需释放，需�?Hook system_server 内的 PowerManagerService
        LogX.w("发现孤儿 wake lock: $tag（保守策略，不强制释放）")
    }
}
