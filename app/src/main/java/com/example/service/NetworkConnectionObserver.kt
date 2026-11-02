package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.FocusNetRepository
import com.example.model.TrafficEventType
import com.example.model.TrafficLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConnectionType(val label: String) {
    WIFI("Rede Wi-Fi"),
    MOBILE_DATA("Dados Móveis (3G/4G/5G)"),
    DISCONNECTED("Sem Conexão")
}

class NetworkConnectionObserver(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _connectionType = MutableStateFlow(determineCurrentConnectionType())
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val type = determineConnectionType(network)
            _connectionType.value = type

            val repo = FocusNetRepository.getInstance(context)
            val settings = repo.settings.value

            if (settings.autoActivateOnConnect && !settings.isProtectionEnabled) {
                scope.launch {
                    repo.addLog(
                        TrafficLogEntry(
                            packageName = context.packageName,
                            appName = "FocusNet",
                            eventType = TrafficEventType.SERVICE_STARTED,
                            detail = "Rede conectada via ${type.label}. Proteção de dados ativada automaticamente!"
                        )
                    )
                    TrafficDirectorManager.toggleProtection(context, true)
                }
            }
        }

        override fun onLost(network: Network) {
            _connectionType.value = determineCurrentConnectionType()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _connectionType.value = determineConnectionType(network)
        }
    }

    fun startListening() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            // Callback registration fallback
        }
    }

    fun stopListening() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    fun determineCurrentConnectionType(): ConnectionType {
        val activeNetwork = connectivityManager.activeNetwork ?: return ConnectionType.DISCONNECTED
        return determineConnectionType(activeNetwork)
    }

    private fun determineConnectionType(network: Network): ConnectionType {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.DISCONNECTED
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE_DATA
            else -> ConnectionType.DISCONNECTED
        }
    }
}
