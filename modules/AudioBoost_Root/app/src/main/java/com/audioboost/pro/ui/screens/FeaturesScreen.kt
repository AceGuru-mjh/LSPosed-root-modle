package com.audioboost.pro.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.ui.components.FeatureCard
import com.audioboost.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: AudioConfig, onConfigChange: (AudioConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音量增强", "系统级音量放大（最??50%音量??,
            cfg.volumeBoostEnabled,
            { val nc = cfg.copy(volumeBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "低音增强", "低频信号增益??0% 起始增强??,
            cfg.bassBoostEnabled,
            { val nc = cfg.copy(bassBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "均衡??, "5段音频均衡器（自定义频段增益??,
            cfg.equalizerEnabled,
            { val nc = cfg.copy(equalizerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("应用层实验??, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "扬声器增??, "独立扬声器增益控??,
            cfg.speakerBoostEnabled,
            { val nc = cfg.copy(speakerBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "麦克风增??, "输入麦克风增益控??,
            cfg.micBoostEnabled,
            { val nc = cfg.copy(micBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音质增强", "44100Hz??8000Hz 采样率提??+ 16??4bit 位深提升",
            cfg.audioQualityEnhanceEnabled,
            { val nc = cfg.copy(audioQualityEnhanceEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 专属（需 Shizuku??, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统音量突破上限", "Shizuku media volume --set 突破系统最大音量限??,
            cfg.systemVolumeBoostEnabled,
            { val nc = cfg.copy(systemVolumeBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "AudioFlinger 节点写入", "Shizuku ??/sys/class/audio/pcm 节点（部分设备支持）",
            cfg.audioFlingerNodeEnabled,
            { val nc = cfg.copy(audioFlingerNodeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 实验??, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "AudioPolicy 全局配置", "Shizuku 修改 AudioPolicy 全局配置",
            cfg.globalAudioPolicyEnabled,
            { val nc = cfg.copy(globalAudioPolicyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku Audio Bridge", "cmd media_audio 桥接音频命令",
            cfg.shizukuAudioBridgeEnabled,
            { val nc = cfg.copy(shizukuAudioBridgeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("系统级增强（Task24 新增??, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "AudioPolicy Hack", "Magisk overlay 持久??audio_policy_configuration.xml??6dB 硬件增益??,
            cfg.audioPolicyHackEnabled,
            { val nc = cfg.copy(audioPolicyHackEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Root v1.1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "tinymix 混音器探??, "Shizuku 执行 tinymix 获取/设置 ALSA 混音器控件（硬件级增益）",
            cfg.tinymixProbeEnabled,
            { val nc = cfg.copy(tinymixProbeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        // 音量增强滑块
        if (cfg.volumeBoostEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("音量增强级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.boostLevel}%", style = MaterialTheme.typography.bodySmall)
            val boostState = remember(cfg) { mutableFloatStateOf(cfg.boostLevel.toFloat()) }
            Slider(
                value = boostState.floatValue,
                onValueChange = { boostState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(boostLevel = boostState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 100f..200f, steps = 19
            )
        }

        // 低音增强滑块
        if (cfg.bassBoostEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("低音增强级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前: ${cfg.bassLevel}%", style = MaterialTheme.typography.bodySmall)
            val bassState = remember(cfg) { mutableFloatStateOf(cfg.bassLevel.toFloat()) }
            Slider(
                value = bassState.floatValue,
                onValueChange = { bassState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(bassLevel = bassState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 0f..100f, steps = 19
            )
        }
    }
}
