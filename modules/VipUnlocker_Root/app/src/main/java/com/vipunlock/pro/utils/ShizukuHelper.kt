package com.vipunlock.pro.utils

import java.lang.reflect.Method

/**
 * Shizuku 联动助手（Root 版）
 *
 * 功能�?
 *  1. 检�?Shizuku 服务是否可用
 *  2. 通过反射调用 Shizuku.newProcess 执行系统�?Shell 命令
 *     - setprop 修改系统属性（ro.product.* 等）
 *     - pm grant 授予隐藏权限
 *     - 修改 /system/etc/hosts（需 root 级）
 *
 * 硬性限制：
 *  - 系统�?Hook 必须先检�?isShizukuAvailable()
 *  - setprop 修改非持久化，重启后消失
 *  - 修改 /system/etc/hosts 需 root 级别 Shizuku 授权
 *  - 所有调用通过 try-catch 保护，失败不影响其他 Hook
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private var shizukuAvailable: Boolean? = null

    /** 检�?Shizuku 是否可用 */
    fun isShizukuAvailable(): Boolean {
        if (shizukuAvailable != null) return shizukuAvailable!!
        shizukuAvailable = try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val method: Method = cls.getMethod("pingBinder")
            val result = method.invoke(null) as? Boolean ?: false
            LogX.d("Shizuku状�? $result")
            result
        } catch (e: Exception) {
            LogX.w("Shizuku不可用或未安�? ${e.message}")
            false
        }
        return shizukuAvailable!!
    }

    /**
     * 通过 Shizuku 执行 shell 命令
     * @return 命令输出（stdout），失败返回 null
     */
    fun execShell(command: String): String? {
        return try {
            if (!isShizukuAvailable()) return null
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuCls.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) ?: return null

            val isMethod = process.javaClass.getMethod("getInputStream")
            val isStr = isMethod.invoke(process) as? java.io.InputStream
            val out = isStr?.bufferedReader()?.readText()

            // 等待进程结束（防止僵死）
            try {
                val waitFor = process.javaClass.getMethod("waitFor")
                waitFor.invoke(process)
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            out
        } catch (e: Exception) {
            LogX.e("Shizuku Shell执行异常: $command", e)
            null
        }
    }

    /** 仅执行不关心输出，返回是否执行成�?*/
    fun execShellSilent(command: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            execShell(command) != null
        } catch (_: Throwable) {
            false
        }
    }

    /** 通过 Shizuku 设置系统属性（setprop�?*/
    fun setSystemProperty(key: String, value: String): Boolean {
        return execShellSilent("setprop $key $value")
    }

    /** 通过 Shizuku 写入文件（需 root 级） */
    fun writeFile(path: String, content: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            val escaped = content.replace("'", "'\\''")
            execShell("echo '$escaped' > $path") != null
        } catch (e: Exception) {
            LogX.e("Shizuku写入文件异常: $path", e)
            false
        }
    }

    /** 通过 Shizuku 读取文件内容 */
    fun readFile(path: String): String? {
        return try {
            if (!isShizukuAvailable()) return null
            execShell("cat $path 2>/dev/null")
        } catch (_: Throwable) { null }
    }

    /** 通过 Shizuku 授予权限：pm grant <pkg> <perm> */
    fun grantPermission(pkg: String, permission: String): Boolean {
        return execShellSilent("pm grant $pkg $permission 2>/dev/null")
    }

    /** 重置 Shizuku 状态（重新检测） */
    fun reset() {
        shizukuAvailable = null
    }

    /**
     * 通过 Shizuku 执行 SQLite 命令
     * @param dbPath 数据库文件路�?
     * @param sql SQL 语句
     * @return 命令输出，失败返�?null
     */
    fun execSqlite(dbPath: String, sql: String): String? {
        return try {
            if (!isShizukuAvailable()) return null
            execShell("sqlite3 '$dbPath' \"$sql\"")
        } catch (e: Throwable) {
            LogX.w("execSqlite 异常: ${e.message}")
            null
        }
    }

    /**
     * 通过 Shizuku 创建 Magisk overlay 模块目录
     * 用于持久化修�?/system 下的属�?配置（重启后仍生效）
     * @param moduleId Magisk 模块 ID
     * @return 是否创建成功
     */
    fun createMagiskOverlay(moduleId: String): Boolean {
        if (!isShizukuAvailable()) return false
        val base = "/data/adb/modules/$moduleId"
        return try {
            execShellSilent("mkdir -p $base/system") &&
            execShellSilent("echo 'id=$moduleId' > $base/module.prop") &&
            execShellSilent("echo 'name=LSP-Model $moduleId' >> $base/module.prop") &&
            execShellSilent("echo 'version=v1.0.11' >> $base/module.prop") &&
            execShellSilent("echo 'author=MJH' >> $base/module.prop") &&
            execShellSilent("echo 'description=Auto-generated by LSP-Model' >> $base/module.prop")
        } catch (e: Throwable) {
            LogX.w("createMagiskOverlay 异常: ${e.message}")
            false
        }
    }


    /** 通过 Shizuku �?Magisk overlay 文件 */
    fun writeMagiskOverlay(moduleId: String, relPath: String, content: String): Boolean {
        val base = "/data/adb/modules/" + moduleId
        val fullPath = base + "/system/" + relPath
        val dir = fullPath.substring(0, fullPath.lastIndexOf('/'))
        execShellSilent("mkdir -p " + dir)
        return writeFile(fullPath, content)
    }

    fun release() {
        shizukuAvailable = null
    }
}
