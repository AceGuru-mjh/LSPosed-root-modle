package com.notifymaster.pro.utils

import kotlin.random.Random

object AntiDetectionHelper {
    fun sleepDuringVerify() {
        try {
            Thread.sleep(Random.nextLong(50, 200))
        } catch (_: Throwable) { }
    }

    fun cleanTraces() {
    }

    fun isSecurityCritical(className: String): Boolean {
        val keywords = listOf(
            "signature", "verify", "check", "integrity",
            "tamper", "cert", "keystore", "security"
        )
        return keywords.any { className.lowercase().contains(it) }
    }
}
