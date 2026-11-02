package com.example.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed Android application and its FocusNet filtering status.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isWhitelisted: Boolean = false,
    val isSystemApp: Boolean = false,
    val category: String = "Aplicativo",
    @Transient val icon: Drawable? = null
)
