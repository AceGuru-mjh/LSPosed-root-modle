package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 网络标识伪造Hook（Root 专属，需 Shizuku root 级授权）
 *
 * 功能??
 *  - 应用??Hook NetworkInterface.getHardwareAddress 保持一??
 *  - 通过 Shizuku 修改 /sys/class/net/wlan0/address ??ip link set 修改网卡 MAC
 *
 * 硬性限制：
 *  - 必须先检??ShizukuHelper.isShizukuAvailable()
 *  - ??/sys/class/net/wlan0/address 需??root 级权限，Shizuku adb 级可能不??
 *  - 修改网卡 MAC 可能导致 Wi-Fi 重连
 */
object NetworkIdentifierHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.networkIdentifierHookEnabled) return
        LogX.i("网络标识伪造启动（Root 专属??)

        hookNetworkInterface(lpparam, cfg.spoofMacAddress)
        modifySystemMacViaShizuku(cfg.spoofMacAddress)
    }

    private fun hookNetworkInterface(lpparam: XC_LoadPackage.LoadPackageParam, spoofMac: String) {
        try {
            val ni = XposedHelpers.findClassIfExists(
                "java.net.NetworkInterface", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(ni, "getHardwareAddress", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = macStringToBytes(spoofMac)
                    }
                })
                LogX.hookSuccess("NetworkInterface", "getHardwareAddress")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("NetworkInterface", "getHardwareAddress", e)
        }
    }

    private fun modifySystemMacViaShizuku(spoofMac: String) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过系统网卡 MAC 修改（仅应用??Hook 生效??)
            return
        }

        val writeResult = ShizukuHelper.writeFile("/sys/class/net/wlan0/address", spoofMac)
        if (writeResult) {
            LogX.i("Shizuku ??/sys/class/net/wlan0/address 成功: $spoofMac")
            return
        }

        val ipLinkResult = ShizukuHelper.execShell("ip link set dev wlan0 address $spoofMac")
        if (ipLinkResult != null) {
            LogX.i("Shizuku ip link set MAC 成功: $spoofMac")
        } else {
            LogX.w("Shizuku 修改网卡 MAC 失败（可能需??root 级授权）")
        }
    }

    /** MAC 字符串转字节数组 "AA:BB:CC:DD:EE:FF" -> byte[6] */
    private fun macStringToBytes(mac: String): ByteArray {
        val parts = mac.split(":")
        if (parts.size != 6) return ByteArray(6)
        return parts.map { it.toInt(16).toByte() }.toByteArray()
    }
}
