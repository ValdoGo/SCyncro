package com.example.model

import android.graphics.drawable.Drawable

data class BlockedAttempt(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val reason: String = "Tentativa de sincronização em 2º plano barrada",
    val networkType: String = "Dados Móveis / Wi-Fi",
    val targetHostOrPort: String = "Porta HTTPS (443)",
    @Transient val icon: Drawable? = null
)
