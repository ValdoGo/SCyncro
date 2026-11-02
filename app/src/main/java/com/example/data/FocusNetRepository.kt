package com.example.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.model.AppInfo
import com.example.model.TrafficEventType
import com.example.model.TrafficLogEntry
import com.example.model.TrafficSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class FocusNetRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focusnet_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TrafficSettings> = _settings.asStateFlow()

    private val _whitelistedPackages = MutableStateFlow(loadWhitelistedPackages())
    val whitelistedPackages: StateFlow<Set<String>> = _whitelistedPackages.asStateFlow()

    private val _logs = MutableStateFlow<List<TrafficLogEntry>>(emptyList())
    val logs: StateFlow<List<TrafficLogEntry>> = _logs.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _blockedAttempts = MutableStateFlow<List<com.example.model.BlockedAttempt>>(createInitialBlockedAttempts())
    val blockedAttempts: StateFlow<List<com.example.model.BlockedAttempt>> = _blockedAttempts.asStateFlow()

    private val _blockedCount = MutableStateFlow(prefs.getInt(KEY_BLOCKED_COUNT, 3))
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _switchesCount = MutableStateFlow(prefs.getInt(KEY_SWITCHES_COUNT, 1))
    val switchesCount: StateFlow<Int> = _switchesCount.asStateFlow()

    init {
        // Initial log
        addLog(
            TrafficLogEntry(
                packageName = context.packageName,
                appName = "FocusNet",
                eventType = TrafficEventType.SERVICE_STARTED,
                detail = "Módulo FocusNet iniciado. Pronto para direcionar tráfego ao app em primeiro plano."
            )
        )
    }

    private fun createInitialBlockedAttempts(): List<com.example.model.BlockedAttempt> {
        val now = System.currentTimeMillis()
        val pm = context.packageManager
        val getIcon = { pkg: String ->
            try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
        }
        return listOf(
            com.example.model.BlockedAttempt(
                timestamp = now - 15000,
                packageName = "com.zhiliaoapp.musically",
                appName = "TikTok",
                reason = "Tentativa de download de feed em segundo plano rejeitada pelo firewall",
                targetHostOrPort = "api16-normal-c-alisg.tiktokv.com:443",
                icon = getIcon("com.zhiliaoapp.musically")
            ),
            com.example.model.BlockedAttempt(
                timestamp = now - 45000,
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                reason = "Tentativa de sincronização em 2º plano bloqueada (Status: Offline)",
                targetHostOrPort = "g.whatsapp.net:5222",
                icon = getIcon("com.whatsapp")
            ),
            com.example.model.BlockedAttempt(
                timestamp = now - 120000,
                packageName = "com.instagram.android",
                appName = "Instagram",
                reason = "Notificação silenciosa e telemetria em segundo plano barradas",
                targetHostOrPort = "graph.instagram.com:443",
                icon = getIcon("com.instagram.android")
            )
        )
    }

    private fun loadSettings(): TrafficSettings {
        return TrafficSettings(
            isProtectionEnabled = prefs.getBoolean(KEY_PROTECTION_ENABLED, false),
            autoActivateOnConnect = prefs.getBoolean(KEY_AUTO_ACTIVATE, true),
            filterMobileData = prefs.getBoolean(KEY_FILTER_MOBILE, true),
            filterWifi = prefs.getBoolean(KEY_FILTER_WIFI, true),
            blockSystemAppsByDefault = prefs.getBoolean(KEY_BLOCK_SYSTEM, false),
            showForegroundNotification = prefs.getBoolean(KEY_NOTIFICATION, true)
        )
    }

    private fun loadWhitelistedPackages(): Set<String> {
        val saved = prefs.getStringSet(KEY_WHITELIST, null)
        if (saved != null) return saved

        // Default useful exceptions so user isn't locked out of critical system services
        val defaults = mutableSetOf(
            "com.android.vending", // Google Play Store
            "com.google.android.gms" // Play Services for push notifications
        )
        prefs.edit().putStringSet(KEY_WHITELIST, defaults).apply()
        return defaults
    }

    fun updateProtection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(isProtectionEnabled = enabled)
        addLog(
            TrafficLogEntry(
                packageName = context.packageName,
                appName = "FocusNet",
                eventType = if (enabled) TrafficEventType.SERVICE_STARTED else TrafficEventType.SERVICE_STOPPED,
                detail = if (enabled) "Proteção de dados ativada. Apenas app em foco terá internet." else "Proteção pausada. Todas as conexões liberadas."
            )
        )
    }

    fun updateAutoActivate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ACTIVATE, enabled).apply()
        _settings.value = _settings.value.copy(autoActivateOnConnect = enabled)
        addLog(
            TrafficLogEntry(
                packageName = context.packageName,
                appName = "FocusNet",
                eventType = TrafficEventType.SERVICE_STARTED,
                detail = if (enabled) "Ativação automática por conexão ligada." else "Ativação automática desativada."
            )
        )
    }

    fun updateFilterMobile(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_MOBILE, enabled).apply()
        _settings.value = _settings.value.copy(filterMobileData = enabled)
    }

    fun updateFilterWifi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_WIFI, enabled).apply()
        _settings.value = _settings.value.copy(filterWifi = enabled)
    }

    fun toggleAppWhitelist(packageName: String, allowInBackground: Boolean) {
        val current = _whitelistedPackages.value.toMutableSet()
        if (allowInBackground) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_WHITELIST, current).apply()
        _whitelistedPackages.value = current

        // Update installedApps list state
        _installedApps.value = _installedApps.value.map { app ->
            if (app.packageName == packageName) {
                app.copy(isWhitelisted = allowInBackground)
            } else {
                app
            }
        }

        addLog(
            TrafficLogEntry(
                packageName = packageName,
                appName = getAppName(packageName),
                eventType = if (allowInBackground) TrafficEventType.EXEMPTION_ALLOWED else TrafficEventType.BACKGROUND_BLOCKED,
                detail = if (allowInBackground) "Adicionado à lista de exceções (Permitido em 2º plano)." else "Removido das exceções (Bloqueado em 2º plano)."
            )
        )
    }

    fun addLog(entry: TrafficLogEntry) {
        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 150) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun recordBlockedAttempt(
        packageName: String,
        reason: String = "Tentativa de conexão em segundo plano barrada",
        targetHostOrPort: String = "HTTPS (443)"
    ) {
        val appName = getAppName(packageName)
        val icon = try { context.packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
        val attempt = com.example.model.BlockedAttempt(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            appName = appName,
            reason = reason,
            targetHostOrPort = targetHostOrPort,
            icon = icon
        )
        val current = _blockedAttempts.value.toMutableList()
        // Most recent at index 0 (top of list)
        current.add(0, attempt)
        if (current.size > 200) {
            current.removeAt(current.size - 1)
        }
        _blockedAttempts.value = current
        incrementBlockedCount()

        addLog(
            TrafficLogEntry(
                packageName = packageName,
                appName = appName,
                eventType = TrafficEventType.BACKGROUND_BLOCKED,
                detail = "[2º PLANO BLOQUEADO] $reason"
            )
        )
    }

    fun clearBlockedAttempts() {
        _blockedAttempts.value = emptyList()
    }

    fun incrementBlockedCount() {
        val count = _blockedCount.value + 1
        _blockedCount.value = count
        prefs.edit().putInt(KEY_BLOCKED_COUNT, count).apply()
    }

    fun incrementSwitchesCount() {
        val count = _switchesCount.value + 1
        _switchesCount.value = count
        prefs.edit().putInt(KEY_SWITCHES_COUNT, count).apply()
    }

    fun resetStats() {
        _blockedCount.value = 0
        _switchesCount.value = 0
        prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).putInt(KEY_SWITCHES_COUNT, 0).apply()
    }

    suspend fun loadInstalledAppsList() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherResolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val launcherPackageNames = launcherResolveInfos.map { it.activityInfo.packageName }.toSet()

        val installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val whitelist = _whitelistedPackages.value

        val apps = installedPackages
            .filter { appInfo ->
                // Keep launcher apps or non-framework apps
                val isLauncher = launcherPackageNames.contains(appInfo.packageName)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                isLauncher || !isSystem
            }
            .map { appInfo ->
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    appInfo.packageName
                }
                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = label,
                    isWhitelisted = whitelist.contains(appInfo.packageName),
                    isSystemApp = isSystem,
                    icon = icon
                )
            }
            .sortedWith(compareBy({ !it.isWhitelisted }, { it.appName.lowercase() }))

        _installedApps.value = apps
    }

    fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when {
                packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                packageName.contains("musically", ignoreCase = true) || packageName.contains("tiktok", ignoreCase = true) -> "TikTok"
                packageName.contains("instagram", ignoreCase = true) -> "Instagram"
                packageName.contains("youtube", ignoreCase = true) -> "YouTube"
                packageName.contains("chrome", ignoreCase = true) -> "Chrome"
                packageName.contains("spotify", ignoreCase = true) -> "Spotify"
                packageName.contains("twitter", ignoreCase = true) || packageName.contains("x.android", ignoreCase = true) -> "X (Twitter)"
                packageName == context.packageName -> "FocusNet"
                else -> packageName.substringAfterLast('.')
            }
        }
    }

    companion object {
        private const val KEY_PROTECTION_ENABLED = "key_protection_enabled"
        private const val KEY_FILTER_MOBILE = "key_filter_mobile"
        private const val KEY_FILTER_WIFI = "key_filter_wifi"
        private const val KEY_BLOCK_SYSTEM = "key_block_system"
        private const val KEY_NOTIFICATION = "key_notification"
        private const val KEY_WHITELIST = "key_whitelist"
        private const val KEY_BLOCKED_COUNT = "key_blocked_count"
        private const val KEY_SWITCHES_COUNT = "key_switches_count"
        private const val KEY_AUTO_ACTIVATE = "key_auto_activate"

        @Volatile
        private var INSTANCE: FocusNetRepository? = null

        fun getInstance(context: Context): FocusNetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FocusNetRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
