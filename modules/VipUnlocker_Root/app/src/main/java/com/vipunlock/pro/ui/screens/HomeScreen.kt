package com.vipunlock.pro.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vipunlock.pro.XposedLoader
import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.services.FloatingBallService
import com.vipunlock.pro.utils.ConfigManager

@Composable
fun HomeScreen(
    cfg: VipConfig,
    onConfigChange: (VipConfig) -> Unit,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val ctx = LocalContext.current
    var importMessage by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val logs = remember { mutableStateListOf<String>() }
    val vipsCount = remember { mutableStateOf(0L) }
    val adsRemovedCount = remember { mutableStateOf(0L) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            if (json.isBlank()) {
                importMessage = "导入失败：文件为空"
                return@rememberLauncherForActivityResult
            }
            val ok = ConfigManager.importConfig(json)
            importMessage = if (ok) {
                onConfigChange(ConfigManager.getGlobalConfig())
                "导入成功"
            } else {
                "导入失败：JSON 格式错误或解析失败"
            }
        } catch (e: Exception) {
            importMessage = "导入失败: ${e.message}"
        }
    }

    fun exportConfig() {
        try {
            val json = ConfigManager.exportConfig()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_TITLE, "VipUnlocker_Root_config.json")
                type = "application/json"
            }
            ctx.startActivity(
                Intent.createChooser(sendIntent, "导出配置到...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            logs.add("[${System.currentTimeMillis()}] 已导出配置")
        } catch (e: Exception) {
            importMessage = "导出失败: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            imageVector = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "切换暗色/亮色模式",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text("VipUnlocker Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("v${XposedLoader.VERSION}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text(
                    "已处理: ${vipsCount.value + adsRemovedCount.value} 次",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("模块总开关", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "开启后所有功能将在目标应用生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = cfg.masterEnabled,
                    onCheckedChange = {
                        val nc = cfg.copy(masterEnabled = it)
                        ConfigManager.saveGlobalConfig(nc)
                        onConfigChange(nc)
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("实时统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    StatBox("已解锁 VIP", vipsCount.value.toString(), modifier = Modifier.weight(1f))
                    StatBox("已去除广告", adsRemovedCount.value.toString(), modifier = Modifier.weight(1f))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("快捷操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { logs.clear() }, modifier = Modifier.weight(1f)) {
                        Text("清空日志")
                    }
                    OutlinedButton(onClick = { exportConfig() }, modifier = Modifier.weight(1f)) {
                        Text("导出")
                    }
                    OutlinedButton(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) {
                        Text("导入")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        startFloatingBall(ctx)
                        logs.add("[${System.currentTimeMillis()}] 已请求启动悬浮球")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启动悬浮球控制面板")
                }
                importMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("控制台", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${logs.size} 条", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.heightIn(max = 200.dp).padding(8.dp)) {
                        if (logs.isEmpty()) {
                            Text("暂无日志", style = MaterialTheme.typography.bodySmall)
                        } else {
                            logs.takeLast(50).forEach { log ->
                                Text(log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

fun startFloatingBall(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
    }
    val intent = Intent(context, FloatingBallService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
