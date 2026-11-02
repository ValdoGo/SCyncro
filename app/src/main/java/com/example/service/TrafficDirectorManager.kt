package com.example.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.example.data.FocusNetRepository
import com.example.model.TrafficEventType
import com.example.model.TrafficLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TrafficDirectorManager {

    private val _currentForegroundPackage = MutableStateFlow<String?>("com.example")
    val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

    private val _currentForegroundAppName = MutableStateFlow<String>("FocusNet")
    val currentForegroundAppName: StateFlow<String> = _currentForegroundAppName.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private var vpnReconfigListener: (() -> Unit)? = null

    fun registerVpnService(onReconfigRequested: () -> Unit) {
        vpnReconfigListener = onReconfigRequested
        _isVpnActive.value = true
    }

    fun unregisterVpnService() {
        vpnReconfigListener = null
        _isVpnActive.value = false
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    /**
     * Called whenever FocusAccessibilityService detects an app change in real-time.
     */
    fun onForegroundAppChanged(packageName: String, context: Context) {
        val previousPkg = _currentForegroundPackage.value
        if (packageName == previousPkg) return

        // Skip fleeting system dialogs or home launcher if desired, but capture real apps
        if (packageName == "com.android.systemui" || packageName.contains("inputmethod")) {
            return
        }

        val repo = FocusNetRepository.getInstance(context)
        val appName = repo.getAppName(packageName)

        _currentForegroundPackage.value = packageName
        _currentForegroundAppName.value = appName
        repo.incrementSwitchesCount()

        val previousAppName = previousPkg?.let { repo.getAppName(it) } ?: "Nenhum"

        // If the previous app moved to background and is not whitelisted, record that its net was cut off
        if (previousPkg != null && previousPkg != packageName && !repo.whitelistedPackages.value.contains(previousPkg)) {
            repo.recordBlockedAttempt(
                packageName = previousPkg,
                reason = "App saiu da tela para segundo plano. Acesso à internet cortado imediatamente.",
                targetHostOrPort = "Servidores de segundo plano"
            )
        }

        repo.addLog(
            TrafficLogEntry(
                packageName = packageName,
                appName = appName,
                eventType = TrafficEventType.FOREGROUND_GRANTED,
                detail = "Foco alternado: $previousAppName -> $appName. Tráfego de internet exclusivo para $appName."
            )
        )

        // If protection is active, trigger immediate firewall re-routing
        if (repo.settings.value.isProtectionEnabled) {
            vpnReconfigListener?.invoke()
        }
    }

    fun toggleProtection(context: Context, enable: Boolean) {
        val repo = FocusNetRepository.getInstance(context)
        repo.updateProtection(enable)

        val intent = Intent(context, FocusNetVpnService::class.java).apply {
            action = if (enable) FocusNetVpnService.ACTION_START else FocusNetVpnService.ACTION_STOP
        }

        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.startService(intent)
        }
    }

    fun isAccessibilityPermissionEnabled(context: Context): Boolean {
        val expectedService = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedService, ignoreCase = true) ||
                componentName.contains("FocusAccessibilityService", ignoreCase = true)
            ) {
                return true
            }
        }
        return _isAccessibilityConnected.value
    }

    fun isVpnPrepared(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    /**
     * In-app test simulator: allows user to simulate switching to WhatsApp, TikTok, etc.
     * inside the emulator or preview to verify real-time switching and logging!
     */
    fun simulateAppSwitch(packageName: String, context: Context) {
        onForegroundAppChanged(packageName, context)
    }
}
