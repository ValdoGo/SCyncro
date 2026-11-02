package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FocusNetRepository
import com.example.model.AppInfo
import com.example.model.TrafficLogEntry
import com.example.model.TrafficSettings
import com.example.service.TrafficDirectorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusNetRepository.getInstance(application)

    val settings: StateFlow<TrafficSettings> = repository.settings
    val whitelistedPackages: StateFlow<Set<String>> = repository.whitelistedPackages
    val logs: StateFlow<List<TrafficLogEntry>> = repository.logs
    val blockedAttempts: StateFlow<List<com.example.model.BlockedAttempt>> = repository.blockedAttempts
    val blockedCount: StateFlow<Int> = repository.blockedCount
    val switchesCount: StateFlow<Int> = repository.switchesCount

    private val connectionObserver = com.example.service.NetworkConnectionObserver(application)
    val connectionType: StateFlow<com.example.service.ConnectionType> = connectionObserver.connectionType

    val currentForegroundPackage: StateFlow<String?> = TrafficDirectorManager.currentForegroundPackage
    val currentForegroundAppName: StateFlow<String> = TrafficDirectorManager.currentForegroundAppName
    val activeGracePasses: StateFlow<Map<String, Long>> = TrafficDirectorManager.activeGracePasses
    val temporaryPasses: StateFlow<Map<String, Long>> = repository.temporaryPasses
    val isVpnActive: StateFlow<Boolean> = TrafficDirectorManager.isVpnActive
    val isAccessibilityConnected: StateFlow<Boolean> = TrafficDirectorManager.isAccessibilityConnected

    private val _isAccessibilityPermissionGranted = MutableStateFlow(false)
    val isAccessibilityPermissionGranted: StateFlow<Boolean> = _isAccessibilityPermissionGranted.asStateFlow()

    private val _isVpnPermissionGranted = MutableStateFlow(false)
    val isVpnPermissionGranted: StateFlow<Boolean> = _isVpnPermissionGranted.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(
        repository.installedApps,
        _searchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshPermissionStatuses()
        connectionObserver.startListening()
        viewModelScope.launch {
            repository.loadInstalledAppsList()
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshPermissionStatuses() {
        val context = getApplication<Application>()
        _isAccessibilityPermissionGranted.value =
            TrafficDirectorManager.isAccessibilityPermissionEnabled(context)
        _isVpnPermissionGranted.value =
            TrafficDirectorManager.isVpnPrepared(context)
    }

    fun toggleProtection(enable: Boolean) {
        TrafficDirectorManager.toggleProtection(getApplication(), enable)
        refreshPermissionStatuses()
    }

    fun toggleMobileFilter(enable: Boolean) {
        repository.updateFilterMobile(enable)
        if (settings.value.isProtectionEnabled) {
            TrafficDirectorManager.toggleProtection(getApplication(), true)
        }
    }

    fun toggleWifiFilter(enable: Boolean) {
        repository.updateFilterWifi(enable)
        if (settings.value.isProtectionEnabled) {
            TrafficDirectorManager.toggleProtection(getApplication(), true)
        }
    }

    fun toggleAppWhitelist(packageName: String, allow: Boolean) {
        repository.toggleAppWhitelist(packageName, allow)
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    fun clearBlockedAttempts() {
        repository.clearBlockedAttempts()
    }

    fun resetStats() {
        repository.resetStats()
    }

    fun toggleAutoActivate(enable: Boolean) {
        repository.updateAutoActivate(enable)
    }

    fun updateGracePeriodSeconds(seconds: Int) {
        repository.updateGracePeriodSeconds(seconds)
    }

    fun grantTemporaryPass(packageName: String, minutes: Int = 5) {
        TrafficDirectorManager.grantTemporaryPass(packageName, minutes, getApplication())
    }

    fun revokeTemporaryPass(packageName: String) {
        TrafficDirectorManager.revokeTemporaryPass(packageName, getApplication())
    }

    fun getAppName(packageName: String): String {
        return repository.getAppName(packageName)
    }

    fun simulateAppSwitch(packageName: String) {
        TrafficDirectorManager.simulateAppSwitch(packageName, getApplication())
    }

    override fun onCleared() {
        connectionObserver.stopListening()
        super.onCleared()
    }
}
