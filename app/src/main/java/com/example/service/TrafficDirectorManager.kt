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
import android.view.inputmethod.InputMethodManager
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
import kotlinx.coroutines.withContext

object TrafficDirectorManager {

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentForegroundPackage = MutableStateFlow<String?>("com.example")
    val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

    private val _currentForegroundAppName = MutableStateFlow<String>("FocusNet")
    val currentForegroundAppName: StateFlow<String> = _currentForegroundAppName.asStateFlow()

    // Multi-app Grace Pass Map: Package -> ExpiryTimestamp
    private val _activeGracePasses = MutableStateFlow<Map<String, Long>>(emptyMap())
    val activeGracePasses: StateFlow<Map<String, Long>> = _activeGracePasses.asStateFlow()

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
        "com.google.android.packageinstaller"
    )

    private var appContext: Context? = null
    private var cachedEnabledImes: Set<String>? = null
    private var lastImeCheckTime: Long = 0L

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

    private fun getEnabledImePackages(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        val cached = cachedEnabledImes
        if (cached != null && now - lastImeCheckTime < 30000L) {
            return cached
        }
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val imes = imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
            cachedEnabledImes = imes
            lastImeCheckTime = now
            imes
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun isIgnoredPackage(packageName: String, context: Context): Boolean {
        if (IGNORED_PACKAGES.contains(packageName)) return true
        val imes = getEnabledImePackages(context)
        return imes.contains(packageName)
    }

    /**
     * Called by FocusAccessibilityService on window changes.
     * Applies keyboard and system package filtering, 300ms debounce, and background UsageStats confirmation.
     */
    fun onForegroundAppChanged(rawPackageName: String, context: Context) {
        if (isIgnoredPackage(rawPackageName, context)) return

        debounceJob?.cancel()
        debounceJob = managerScope.launch(Dispatchers.Default) {
            // 300 ms debounce to ignore transient popups or fast gesture swipes
            delay(300)

            // Confirm with UsageStatsManager on background thread
            val confirmedPackage = getConfirmedForegroundApp(rawPackageName, context)
            if (isIgnoredPackage(confirmedPackage, context)) return@launch

            withContext(Dispatchers.Main) {
                applyForegroundSwitch(confirmedPackage, context)
            }
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

        // If user returned to an app in grace period, remove it from grace as it's now foreground
        if (_activeGracePasses.value.containsKey(newPackage)) {
            val updated = _activeGracePasses.value.toMutableMap()
            updated.remove(newPackage)
            _activeGracePasses.value = updated
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
        val now = System.currentTimeMillis()
        val expiry = now + (seconds * 1000L)
        val current = _activeGracePasses.value.toMutableMap()
        current[pkg] = expiry
        _activeGracePasses.value = current

        val repo = FocusNetRepository.getInstance(context)
        repo.addLog(
            TrafficLogEntry(
                packageName = pkg,
                appName = appName,
                eventType = TrafficEventType.EXEMPTION_ALLOWED,
                detail = "Período de graça iniciado ($seconds s) para $appName para concluir uploads/downloads."
            )
        )

        // Reconfigure VPN so grace package stays allowed during grace period
        if (repo.settings.value.isProtectionEnabled) {
            vpnReconfigListener?.invoke()
        }

        ensureGraceTickerRunning(context)
    }

    private fun ensureGraceTickerRunning(context: Context) {
        if (graceJob?.isActive == true) return

        graceJob = managerScope.launch {
            while (_activeGracePasses.value.isNotEmpty()) {
                delay(1000)
                val now = System.currentTimeMillis()
                val current = _activeGracePasses.value
                val expired = current.filterValues { it <= now }
                if (expired.isNotEmpty()) {
                    val remaining = current.filterValues { it > now }
                    _activeGracePasses.value = remaining

                    val repo = FocusNetRepository.getInstance(context)
                    expired.forEach { (pkg, _) ->
                        val appName = repo.getAppName(pkg)
                        repo.recordBlockedAttempt(
                            packageName = pkg,
                            reason = "Período de graça expirado para $appName. Conexão em 2º plano cortada pelo firewall."
                        )
                    }

                    if (repo.settings.value.isProtectionEnabled) {
                        vpnReconfigListener?.invoke()
                    }
                }
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

                if (latestActivityPkg != null && !isIgnoredPackage(latestActivityPkg, context)) {
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
