package com.example.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import com.example.data.FocusNetRepository
import com.example.model.TrafficEventType
import com.example.model.TrafficLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TrafficDirectorManager {

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentForegroundPackage = MutableStateFlow<String?>("com.example")
    val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

    private val _currentForegroundAppName = MutableStateFlow<String>("FocusNet")
    val currentForegroundAppName: StateFlow<String> = _currentForegroundAppName.asStateFlow()

    private val _gracePackage = MutableStateFlow<String?>(null)
    val gracePackage: StateFlow<String?> = _gracePackage.asStateFlow()

    private val _graceRemainingSeconds = MutableStateFlow(0)
    val graceRemainingSeconds: StateFlow<Int> = _graceRemainingSeconds.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private var vpnReconfigListener: (() -> Unit)? = null
    private var debounceJob: Job? = null
    private var graceJob: Job? = null
    private var tempPassCheckerJob: Job? = null

    private val IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "android",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey"
    )

    private var appContext: Context? = null

    fun registerVpnService(context: Context, onReconfigRequested: () -> Unit) {
        appContext = context.applicationContext
        vpnReconfigListener = onReconfigRequested
        _isVpnActive.value = true
        startTemporaryPassChecker()
    }

    fun unregisterVpnService() {
        vpnReconfigListener = null
        _isVpnActive.value = false
        tempPassCheckerJob?.cancel()
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        if (IGNORED_PACKAGES.contains(packageName)) return true
        if (packageName.contains(".inputmethod") ||
            packageName.contains(".ime") ||
            packageName.contains("keyboard", ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    /**
     * Called by FocusAccessibilityService on window changes.
     * Applies ignored package filtering, 300ms debounce, and optional UsageStats confirmation.
     */
    fun onForegroundAppChanged(rawPackageName: String, context: Context) {
        if (isIgnoredPackage(rawPackageName)) return

        debounceJob?.cancel()
        debounceJob = managerScope.launch {
            // 300 ms debounce to ignore transient popups or fast gesture swipes
            delay(300)

            // Confirm with UsageStatsManager if permission is granted
            val confirmedPackage = getConfirmedForegroundApp(rawPackageName, context)
            if (isIgnoredPackage(confirmedPackage)) return@launch

            applyForegroundSwitch(confirmedPackage, context)
        }
    }

    private fun applyForegroundSwitch(newPackage: String, context: Context) {
        val previousPkg = _currentForegroundPackage.value
        if (newPackage == previousPkg) return

        val repo = FocusNetRepository.getInstance(context)
        val appName = repo.getAppName(newPackage)
        val previousAppName = previousPkg?.let { repo.getAppName(it) } ?: "Nenhum"

        _currentForegroundPackage.value = newPackage
        _currentForegroundAppName.value = appName
        repo.incrementSwitchesCount()

        // If the user re-opened the app currently in grace period, cancel the grace countdown
        if (newPackage == _gracePackage.value) {
            graceJob?.cancel()
            _gracePackage.value = null
            _graceRemainingSeconds.value = 0
        }

        // Handle grace period for the app leaving the screen
        if (previousPkg != null &&
            previousPkg != newPackage &&
            previousPkg != context.packageName &&
            !repo.whitelistedPackages.value.contains(previousPkg)
        ) {
            val graceSeconds = repo.settings.value.gracePeriodSeconds
            if (graceSeconds > 0) {
                startGracePeriod(previousPkg, previousAppName, graceSeconds, context)
            } else {
                repo.recordBlockedAttempt(
                    packageName = previousPkg,
                    reason = "App saiu da tela para segundo plano. Internet cortada sem carência."
                )
            }
        }

        repo.addLog(
            TrafficLogEntry(
                packageName = newPackage,
                appName = appName,
                eventType = TrafficEventType.FOREGROUND_GRANTED,
                detail = "Foco alternado: $previousAppName -> $appName. Tráfego exclusivo liberado."
            )
        )

        // Trigger immediate firewall update
        if (repo.settings.value.isProtectionEnabled) {
            vpnReconfigListener?.invoke()
        }
    }

    private fun startGracePeriod(pkg: String, appName: String, seconds: Int, context: Context) {
        graceJob?.cancel()
        _gracePackage.value = pkg
        _graceRemainingSeconds.value = seconds

        val repo = FocusNetRepository.getInstance(context)
        repo.addLog(
            TrafficLogEntry(
                packageName = pkg,
                appName = appName,
                eventType = TrafficEventType.EXEMPTION_ALLOWED,
                detail = "Período de graça iniciado ($seconds s) para concluir uploads/downloads."
            )
        )

        // Reconfigure VPN so grace package stays allowed during grace period
        if (repo.settings.value.isProtectionEnabled) {
            vpnReconfigListener?.invoke()
        }

        graceJob = managerScope.launch {
            for (remaining in seconds downTo 1) {
                _graceRemainingSeconds.value = remaining
                delay(1000)
            }

            // Grace period ended
            _gracePackage.value = null
            _graceRemainingSeconds.value = 0

            repo.recordBlockedAttempt(
                packageName = pkg,
                reason = "Período de graça expirado ($seconds s). Conexão em 2º plano cortada pelo firewall."
            )

            if (repo.settings.value.isProtectionEnabled) {
                vpnReconfigListener?.invoke()
            }
        }
    }

    /**
     * Cross-checks with UsageStatsManager if permission is granted for 100% precision.
     */
    private fun getConfirmedForegroundApp(fallbackPackage: String, context: Context): String {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }

            if (mode == AppOpsManager.MODE_ALLOWED) {
                val usageStatsManager =
                    context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 3000 // last 3 seconds
                val events = usageStatsManager.queryEvents(beginTime, endTime)
                val event = UsageEvents.Event()
                var latestActivityPkg: String? = null

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        latestActivityPkg = event.packageName
                    }
                }

                if (latestActivityPkg != null && !isIgnoredPackage(latestActivityPkg)) {
                    return latestActivityPkg
                }
            }
        } catch (e: Exception) {
            // Usage stats check failed, fall back
        }
        return fallbackPackage
    }

    private fun startTemporaryPassChecker() {
        tempPassCheckerJob?.cancel()
        tempPassCheckerJob = managerScope.launch {
            while (_isVpnActive.value) {
                delay(10000) // check every 10 seconds
                val ctx = appContext ?: return@launch
                val hasExpired = FocusNetRepository.getInstance(ctx).cleanExpiredTemporaryPasses()
                if (hasExpired) {
                    vpnReconfigListener?.invoke()
                }
            }
        }
    }

    fun grantTemporaryPass(packageName: String, minutes: Int, context: Context) {
        val repo = FocusNetRepository.getInstance(context)
        repo.grantTemporaryPass(packageName, minutes)
        if (repo.settings.value.isProtectionEnabled) {
            vpnReconfigListener?.invoke()
        }
    }

    fun revokeTemporaryPass(packageName: String, context: Context) {
        val repo = FocusNetRepository.getInstance(context)
        repo.revokeTemporaryPass(packageName)
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

    fun simulateAppSwitch(packageName: String, context: Context) {
        onForegroundAppChanged(packageName, context)
    }
}
