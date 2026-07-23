# LSPosed Root 模块合集

> 9 个基于 LSPosed 框架的 Root Xposed 模块 · 需 Root + Magisk + LSPosed · 系统级 Hook 能力

<p align="center">
  <img src="https://img.shields.io/badge/Modules-9-rose" />
  <img src="https://img.shields.io/badge/Mode-Root%20%2B%20Magisk-rose" />
  <img src="https://img.shields.io/badge/Framework-LSPosed-rose" />
  <img src="https://img.shields.io/badge/MinSDK-26-rose" />
  <img src="https://img.shields.io/badge/License-MIT-rose" />
</p>

## 模块列表

| 模块 | 包名 | 功能 |
|------|------|------|
| AdBlockerX_Root | com.adblockerx.root | 广告拦截（系统级 hosts + DPI 过滤） |
| PrivacyGuard_Root | com.privacyguard.root | 隐私保护（系统级 SELinux 策略） |
| GameUnlockerPro_Root | com.gameunlocker.pro | 游戏加速（温控绕过 + GPU 调频） |
| BatteryOptimizer_Root | com.batteryopt.root | 省电优化（Doze 模式 + 冻结后台） |
| NotifyMaster_Root | com.notifymaster.root | 通知管理（系统通知通道控制） |
| VipUnlocker_Root | com.vipunlocker.root | VIP 解锁（系统级签名校验绕过） |
| VideoSaver_Root | com.videosaver.root | 视频下载（系统级抓包） |
| StepModifier_Root | com.stepmod.root | 步数修改（系统传感器注入） |
| AudioBoost_Root | com.audioboost.root | 音量增强（系统音频通道修改） |

## 与 NoRoot 版的区别

| 特性 | Root 版 | NoRoot 版 |
|------|---------|-----------|
| 框架 | LSPosed + Magisk | LSPatch |
| 权限 | Root + Shizuku | 仅 Shizuku |
| 系统操作 | 直接读写 /sys /proc | 反射调用 Shizuku API |
| 温控绕过 | ThermalBypassHook（写 sysfs） | 不支持 |
| GPU 调频 | GPUSchedulerHook（写 kgsl） | 不支持 |
| 系统属性 | setprop 直接设置 | 通过 Shizuku adb 命令 |
| 兼容性 | 仅 Root 设备 | 所有设备 |

## 核心特性

- **系统级 Hook 能力**：直接读写 /sys /proc，setprop 系统属性
- **Magisk 集成**：支持 Magisk overlay 持久化
- **ThermalBypassHook**：绕过温控降频（写 /sys/class/thermal/）
- **GPUSchedulerHook**：GPU 调频（写 /sys/class/kgsl/）
- **三大铁律**：与 NoRoot 版相同的架构健壮性
- **Shizuku 辅助**：部分操作通过 Shizuku 执行 adb 命令

## 三大铁律

与 NoRoot 版完全相同的铁律要求：

1. **铁律 1**：XposedLoader 禁止 import hooks/*
2. **铁律 2**：Hook 必须用 Class.forName() 反射调用
3. **铁律 3**：进程双分支（自身进程走 UI，宿主进程走 Hook）

详见 [LSPatch-Noroot-modle](https://github.com/AceGuru-mjh/LSPatch-Noroot-modle) 的铁律文档。

## 使用方法

### 前置要求

- 已 Root 的 Android 设备
- 已安装 Magisk
- 已安装 LSPosed（Zygisk 版）

### 安装步骤

1. 从 [Releases](https://github.com/AceGuru-mjh/LSPosed-root-modle/releases) 下载模块 APK
2. 安装模块 APK
3. 打开 LSPosed 管理器
4. 启用模块，勾选目标 APP 作用域
5. 重启目标 APP，模块自动注入

## 构建

```bash
cd modules/GameUnlockerPro_Root
gradle wrapper --gradle-version 8.2
./gradlew :app:assembleRelease
```

## 技术栈

- **框架**：LSPosed + Magisk
- **语言**：Kotlin 1.9.20
- **UI**：Jetpack Compose + Material 3
- **Xposed API**：compileOnly de.robv.android.xposed:api:82
- **Shizuku API**：compileOnly dev.rikka.shizuku:api:13.1.5
- **构建**：AGP 8.2.0 + Gradle 8.2 + JDK 17
- **minSdk**：26 (Android 8.0)
- **targetSdk**：34 (Android 14)

## Root 专属 Hook

### ThermalBypassHook（温控绕过）

```kotlin
// 直接写 sysfs 节点（仅 Root 可用）
File("/sys/class/thermal/thermal_zone0/temp").writeText("35000")
```

### GPUSchedulerHook（GPU 调频）

```kotlin
// 直接写 GPU 调频节点
File("/sys/class/kgsl/kgsl-3d0/gpuclk").writeText("840000000")
```

### SystemPropertyHook（系统属性）

```kotlin
// setprop 直接设置（Root 权限）
Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop debug.hwui.renderer opengl"))
```

## 项目结构

```
modules/
├── AdBlockerX_Root/             # 每个模块独立 Gradle 工程
│   └── app/src/main/java/com/adblockerx/root/
│       ├── XposedLoader.kt      # 入口
│       ├── hooks/               # 含 Root 专属 Hook
│       │   ├── ThermalBypassHook.kt
│       │   ├── GPUSchedulerHook.kt
│       │   └── ...
│       └── ...
├── keystore/
└── ...其他 8 个模块
.github/workflows/build.yml
```

## 相关链接

- **LSPosed 框架**：https://github.com/LSPosed/LSPosed
- **NoRoot 版**：https://github.com/AceGuru-mjh/LSPatch-Noroot-modle
- **Magisk**：https://github.com/topjohnwu/Magisk

## 开发者

**MJH** - [@AceGuru-mjh](https://github.com/AceGuru-mjh)

## License

MIT
