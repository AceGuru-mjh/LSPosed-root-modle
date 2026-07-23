package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku直接写入健康APP数据库（Root 专属??
 *
 * 通过 Shizuku 执行系统级操作??
 * 硬性限制：需 Shizuku root 级授??
 */
object HealthDatabaseInjectHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.healthDbInjectEnabled) return
        LogX.i("HealthDatabaseInjectHook 启动（Root 专属??)

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过HealthDatabaseInjectHook")
                            return
                        }
                        execute()
                        LogX.i("HealthDatabaseInjectHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("HealthDatabaseInjectHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->HealthDatabaseInjectHook")
    }

    private fun execute() {
        val healthApps = mapOf(
            "com.xiaomi.hm.health" to "/data/data/com.xiaomi.hm.health/databases/health.db",
            "com.huawei.health" to "/data/data/com.huawei.health/databases/health.db",
            "com.sec.android.app.shealth" to "/data/data/com.sec.android.app.shealth/databases/shealth.db",
            "com.oppo.health" to "/data/data/com.oppo.health/databases/health.db",
            "com.vivo.health" to "/data/data/com.vivo.health/databases/health.db",
            "com.google.android.apps.fitness" to "/data/data/com.google.android.apps.fitness/databases/fitness.db"
        )

        val sqlsPerDb = mapOf(
            "com.xiaomi.hm.health" to "UPDATE step_table SET steps=10000 WHERE date=strftime('%Y-%m-%d','now')",
            "com.huawei.health" to "UPDATE step_table SET steps=10000 WHERE date=strftime('%Y-%m-%d','now')",
            "com.sec.android.app.shealth" to "UPDATE daily_steps SET step_count=10000 WHERE date=date('now')",
            "com.oppo.health" to "UPDATE step_records SET steps=10000 WHERE date=date('now')",
            "com.vivo.health" to "UPDATE step_records SET steps=10000 WHERE date=date('now')",
            "com.google.android.apps.fitness" to "UPDATE entry SET value=10000 WHERE name='step_count' AND date=date('now')"
        )

        for ((pkg, db) in healthApps) {
            val sql = sqlsPerDb[pkg] ?: "UPDATE step_table SET steps=10000 WHERE date=strftime('%Y-%m-%d','now')"
            val tables = ShizukuHelper.execShell("sqlite3 '$db' '.tables'")
            LogX.d("$pkg 数据库表: $tables")
            val result = ShizukuHelper.execSqlite(db, sql)
            if (result != null) {
                LogX.d("已写??$pkg 步数数据??)
            } else {
                val fallback = ShizukuHelper.execSqlite(db, sqlsPerDb.values.first())
                if (fallback != null) LogX.d("$pkg 使用通用SQL写入成功")
            }
        }
    }
}
