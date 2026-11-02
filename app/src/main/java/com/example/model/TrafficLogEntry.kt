package com.example.model

enum class TrafficEventType {
    FOREGROUND_GRANTED,
    BACKGROUND_BLOCKED,
    EXEMPTION_ALLOWED,
    SERVICE_STARTED,
    SERVICE_STOPPED,
    SWITCH_DETECTED
}

data class TrafficLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val eventType: TrafficEventType,
    val detail: String
)
