package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 持久化步数注入服务（Root 专属 - Magisk overlay）
 *
 * 功能：
 *  - 通过 Shizuku 创建 Magisk 模块 /data/adb/modules/stepmod/
 *  - 写入 service.sh 脚本，loop 写入步数值到内核节点
 *  - 写入 post-fs-data.sh 用于开机自启
 *  - 重启后依然生效，提供持久化步数注入
 *
 * 限制：
 *  - 需 Magisk + Shizuku root 授权
 *  - 内核节点机型相关，可能路径不通用
 */
object PersistStepInjectHook {

    private const val MODULE_ID = "stepmod"
    private const val MODULE_BASE = "/data/adb/modules/$MODULE_ID"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.persistStepInjectEnabled) return
        LogX.i("PersistStepInjectHook 启动（Root 专属）")

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过持久化步数注入")
            return
        }

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        setupMagiskModule()
                        LogX.i("PersistStepInjectHook Magisk模块创建完成")
                    } catch (e: Throwable) {
                        LogX.w("PersistStepInjectHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->PersistStepInject")
    }

    private fun setupMagiskModule() {
        ShizukuHelper.execShellSilent("mkdir -p $MODULE_BASE")

        val moduleProp = """
            |id=$MODULE_ID
            |name=LSP-Model StepModifier Persistent
            |version=v1.0.11
            |versionCode=1011
            |author=MJH
            |description=Persistent step counter injection service
        """.trimMargin()
        ShizukuHelper.execShellSilent("cat > $MODULE_BASE/module.prop << 'PROPEOF'\n$moduleProp\nPROPEOF")

        val serviceSh = """
            |#!/system/bin/sh
            |STEPS=\${1:-10000}
            |STEP_COUNTER_NODE="/sys/class/sensors/step_counter/value"
            |STEP_DETECTOR_NODE="/sys/class/sensors/step_detector/value"
            |
            |write_step() {
            |  local node=\$1
            |  local val=\$2
            |  if [ -w "\$node" ]; then
            |    echo "\$val" > "\$node" 2>/dev/null
            |  fi
            |}
            |
            |current_step=0
            |while true; do
            |  write_step "\$STEP_COUNTER_NODE" "\$current_step"
            |  write_step "\$STEP_DETECTOR_NODE" "1"
            |  current_step=\$((current_step + (STEPS / 86400 * 10)))
            |  if [ \$current_step -ge \$STEPS ]; then
            |    current_step=\$STEPS
            |  fi
            |  sleep 10
            |done
        """.trimMargin()
        ShizukuHelper.execShellSilent("cat > $MODULE_BASE/service.sh << 'SERVEOF'\n$serviceSh\nSERVEOF")

        val postFsSh = """
            |#!/system/bin/sh
            |chmod 644 /sys/class/sensors/step_counter/value 2>/dev/null
            |chmod 644 /sys/class/sensors/step_detector/value 2>/dev/null
        """.trimMargin()
        ShizukuHelper.execShellSilent("cat > $MODULE_BASE/post-fs-data.sh << 'POSTEOF'\n$postFsSh\nPOSTEOF")

        ShizukuHelper.execShellSilent("chmod 755 $MODULE_BASE/service.sh")
        ShizukuHelper.execShellSilent("chmod 755 $MODULE_BASE/post-fs-data.sh")

        LogX.i("Magisk stepmod 模块已部署到 $MODULE_BASE")
    }
}
