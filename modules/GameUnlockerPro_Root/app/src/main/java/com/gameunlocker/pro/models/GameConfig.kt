package com.gameunlocker.pro.models

/**
 * 单个游戏配置（Root 版）
 *
 * ??NoRoot 配置基础上新增系统级（需 Shizuku 授权）能力：
 *  - thermalBypassEnabled: 温控屏蔽（Hook PowerManager / HardwarePropertiesManager??
 *  - customThermalThreshold: 自定义温控阈??
 *  - gpuOptimizeEnabled: GPU 调频优化（Hook EGL/Choreographer??
 *  - shizukuBridgeEnabled: Shizuku 系统属性修改（setprop 刷新率属性）
 *  - gameModeActivationEnabled: [实验] 通过 Shizuku 执行 cmd game_mode / settings put global game_mode
 *  - cpuBigCoreAffinityEnabled: [实验] 通过 Shizuku ??/sys/devices/system/cpu/cpuN/cpufreq 亲和??
 */
data class GameConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // 机型伪装
    var deviceSpoofEnabled: Boolean = true,
    var selectedDeviceProfileId: String = "xiaomi15",
    var customDeviceProfile: DeviceProfile? = null,

    // 帧率解锁
    var frameRateUnlockEnabled: Boolean = true,
    var targetFps: Int = 120,

    // 环境隐藏
    var detectionHideEnabled: Boolean = true,
    var hideShizuku: Boolean = true,
    var hideXposed: Boolean = true,
    var hideLspatch: Boolean = true,

    // 进程优化
    var processOptimizeEnabled: Boolean = true,

    // 分辨率伪??
    var resolutionSpoofEnabled: Boolean = false,
    var spoofWidth: Int = 2560,
    var spoofHeight: Int = 1440,
    var spoofDpi: Int = 560,

    // ===== 系统级（需 Shizuku/Root 授权??====
    var thermalBypassEnabled: Boolean = false,
    var customThermalThreshold: Int = 50,
    var gpuOptimizeEnabled: Boolean = false,
    var shizukuBridgeEnabled: Boolean = false,

    // ===== 实验性功??=====
    // 应用层实验??
    var touchSamplingBoostEnabled: Boolean = false,
    var networkLatencyOptEnabled: Boolean = false,
    var audioPriorityBoostEnabled: Boolean = false,
    var memoryDefragEnabled: Boolean = false,
    // 系统级实验??
    var gameModeActivationEnabled: Boolean = false,
    var cpuBigCoreAffinityEnabled: Boolean = false,

    // ===== 系统级增强（Task24 新增??====
    /** 内核参数优化（IO 调度??noop + 大核强制在线 + performance governor + sched_child_runs_first=0??*/
    var kernelTunerEnabled: Boolean = false,
    /** GPU 频率锁定（锁??GPU devfreq 为最大频??+ performance governor??*/
    var gpuFreqLockEnabled: Boolean = false,

    var lastModified: Long = 0L
)
