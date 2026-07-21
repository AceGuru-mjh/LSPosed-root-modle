package com.gameunlocker.pro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.ui.components.FeatureCard
import com.gameunlocker.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: GameConfig, onConfigChange: (GameConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        // ===== 应用层功�?=====
        Text("应用层功�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "机型伪装", "伪装 Build/SystemProperties 为旗舰机型，规避机型检�?,
            cfg.deviceSpoofEnabled,
            { val nc = cfg.copy(deviceSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "帧率解锁", "Hook Display/Surface/Unity/Unreal 强制目标帧率",
            cfg.frameRateUnlockEnabled,
            { val nc = cfg.copy(frameRateUnlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "环境隐藏", "隐藏 Xposed/Shizuku/LSPatch/Magisk 等敏感环�?,
            cfg.detectionHideEnabled,
            { val nc = cfg.copy(detectionHideEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "进程优化", "提升渲染线程优先�?+ Shizuku 冻结后台进程",
            cfg.processOptimizeEnabled,
            { val nc = cfg.copy(processOptimizeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "分辨率伪�?, "伪装 Display/DisplayMetrics �?2K，强制加载高清材�?,
            cfg.resolutionSpoofEnabled,
            { val nc = cfg.copy(resolutionSpoofEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 系统级功能（需 Shizuku�?====
        Text("系统级功能（需 Shizuku�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "温控屏蔽", "Hook HardwarePropertiesManager/PowerManager/厂商温控服务屏蔽降频",
            cfg.thermalBypassEnabled,
            { val nc = cfg.copy(thermalBypassEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "GPU 调频优化", "Hook EGL/Choreographer/HardwareRenderer 优化渲染管线",
            cfg.gpuOptimizeEnabled,
            { val nc = cfg.copy(gpuOptimizeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 系统属性修�?, "通过 Shizuku setprop 修改 ro.surface_flinger.* 刷新率属�?,
            cfg.shizukuBridgeEnabled,
            { val nc = cfg.copy(shizukuBridgeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true
        )

        Spacer(Modifier.height(20.dp))

        // ===== 实验�?- 应用�?=====
        Text("实验性功能（应用层）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "触摸采样率提�?, "Hook InputEventReceiver/InputQueue 提升事件线程优先�?,
            cfg.touchSamplingBoostEnabled,
            { val nc = cfg.copy(touchSamplingBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网络延迟优化", "Hook Socket 设置 TCP_NODELAY + 扩大接收缓冲�?,
            cfg.networkLatencyOptEnabled,
            { val nc = cfg.copy(networkLatencyOptEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音频优先级提�?, "Hook AudioTrack �?PERFORMANCE_MODE_LOW_LATENCY + 线程优先�?,
            cfg.audioPriorityBoostEnabled,
            { val nc = cfg.copy(audioPriorityBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内存整理", "Hook MemoryInfo/TrimMemory 让游戏看到更充足内存 + GC 提示",
            cfg.memoryDefragEnabled,
            { val nc = cfg.copy(memoryDefragEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))

        // ===== 实验�?- 系统�?=====
        Text("实验性功能（系统级，需 Shizuku�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "游戏模式激�?, "通过 Shizuku 执行 cmd game_mode / settings put global game_mode",
            cfg.gameModeActivationEnabled,
            { val nc = cfg.copy(gameModeActivationEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, systemLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "CPU 大核亲和�?, "通过 Shizuku �?/sys/devices/system/cpu/cpuN/cpufreq 节点",
            cfg.cpuBigCoreAffinityEnabled,
            { val nc = cfg.copy(cpuBigCoreAffinityEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, systemLevel = true
        )

        Spacer(Modifier.height(20.dp))

        // ===== Task24 系统级增�?=====
        Text("系统级增强（Task24 新增�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内核参数优化", "IO 调度�?noop + 大核强制在线 + performance governor + sched 优化",
            cfg.kernelTunerEnabled,
            { val nc = cfg.copy(kernelTunerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "GPU 频率锁定", "锁定 GPU devfreq 为最大频�?+ performance governor",
            cfg.gpuFreqLockEnabled,
            { val nc = cfg.copy(gpuFreqLockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true
        )

        Spacer(Modifier.height(20.dp))
        if (cfg.frameRateUnlockEnabled) {
            Text("目标帧率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.targetFps} fps", style = MaterialTheme.typography.bodySmall)
            val targetFpsState = remember(cfg) { mutableFloatStateOf(cfg.targetFps.toFloat()) }
            Slider(
                value = targetFpsState.floatValue,
                onValueChange = { targetFpsState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(targetFps = targetFpsState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 60f..160f, steps = 19
            )
    }
}

}
