# LSPosed Root 模块合集

> 需 **Root + Magisk/LSPosed + Shizuku** · 10 个系统级增强模块

## 模块列表

| # | 模块 | 功能 | 关键能力 |
|---|------|------|---------|
| 1 | **ShizukuSceneFix** | Shizuku 授权修复 | pm grant / am broadcast / 直写 authorization.xml |
| 2 | **GameUnlockerPro** | 游戏性能 | 帧率解锁 / 温控绕过(sysfs) / GPU调频 / 内核调优 / 机型伪装 |
| 3 | **PrivacyGuard** | 隐私保护 | 设备ID伪造 / Magisk overlay 持久化 / SELinux chcon / /proc/cmdline挂载 |
| 4 | **AdBlockerX** | 广告拦截 | 系统hosts / PrivateDNS / DNS缓存刷新 / iptables / DoH |
| 5 | **BatteryOptimizer** | 省电优化 | Doze强制 / 应用冻结 / CPU governor / zram优化 / GPU降压 / appops |
| 6 | **StepModifier** | 步数修改 | 传感器HAL直写 / Google Fit/华为/小米 health DB注入 |
| 7 | **NotifyMaster** | 通知管理 | notification_policy.xml直写 / 监听器注入 / 全局通知队列 |
| 8 | **AudioBoost** | 音量增强 | tinymix硬件增益 / audio_effects.xml注入 / AudioPolicy修改 |
| 9 | **VipUnlocker** | VIP解锁 | Play Store DB修改 / Magisk overlay持久化 / 系统属性伪装 |
| 10 | **VideoSaver** | 视频下载 | screencap/screenrecord / 系统代理 / 媒体扫描触发 |

## 前置条件

1. **Magisk** — 获取 Root 权限
2. **LSPosed** — Xposed 框架（Zygisk 模式）
3. **Shizuku** — adb 级系统命令授权

## 安装

```bash
# 1. 下载最新 Release APK
# 2. 安装 APK
# 3. 在 LSPosed 管理器中勾选模块 + 目标应用作用域
# 4. 在 Shizuku 中授权模块
# 5. 重启目标应用生效
```

## 下载

[📦 Releases](https://github.com/AceGuru-mjh/LSPosed-root-modle/releases)

## 构建

```bash
# 单模块编译
cd modules/GameUnlockerPro_Root
gradle wrapper --gradle-version 8.2
./gradlew :app:assembleRelease

# CI 自动编译全部模块（GitHub Actions）
# 推送至 main 分支自动触发 matrix 构建 + Release
```

## Root 能力体系

所有模块通过 Shizuku 实现系统级操作：

| 能力 | 应用模块 |
|------|---------|
| sysfs 节点写入 | GameUnlockerPro, BatteryOptimizer, StepModifier |
| Magisk overlay | PrivacyGuard, VipUnlocker, AdBlockerX |
| pm / am 命令 | ShizukuSceneFix, VipUnlocker |
| sqlite3 DB 直写 | StepModifier, VipUnlocker |
| settings put | BatteryOptimizer, AdBlockerX, NotifyMaster |
| tinymix 硬件控制 | AudioBoost |
| iptables / mount | AdBlockerX, PrivacyGuard |

## 开发者

**MJH** · [GitHub](https://github.com/AceGuru-mjh)
