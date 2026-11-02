package com.example.model

data class TrafficSettings(
    val isProtectionEnabled: Boolean = false,
    val autoActivateOnConnect: Boolean = true,
    val gracePeriodSeconds: Int = 20,
    val filterMobileData: Boolean = true,
    val filterWifi: Boolean = true,
    val blockSystemAppsByDefault: Boolean = false,
    val showForegroundNotification: Boolean = true
)
