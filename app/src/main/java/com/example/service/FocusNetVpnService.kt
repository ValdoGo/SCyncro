package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.FocusNetRepository
import com.example.model.TrafficEventType
import com.example.model.TrafficLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.IOException

class FocusNetVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var packetDrainJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundWithNotification()
                TrafficDirectorManager.registerVpnService {
                    reconfigureTunnel()
                }
                reconfigureTunnel()
                return START_STICKY
            }
            ACTION_RECONFIGURE -> {
                reconfigureTunnel()
                return START_STICKY
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val repo = FocusNetRepository.getInstance(applicationContext)
        val activeAppName = TrafficDirectorManager.currentForegroundAppName.value
        val notification = buildNotification(activeAppName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(activeAppName: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FocusNetVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FocusNet: Internet em Primeiro Plano")
            .setContentText("Liberado para $activeAppName. Apps em segundo plano bloqueados.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Pausar", stopIntent)
            .build()
    }

    @Synchronized
    private fun reconfigureTunnel() {
        val repo = FocusNetRepository.getInstance(applicationContext)
        val settings = repo.settings.value

        if (!settings.isProtectionEnabled) {
            stopVpn()
            return
        }

        // Check active network type
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val shouldFilter = (isWifi && settings.filterWifi) || (isCellular && settings.filterMobileData) || (!isWifi && !isCellular)

        if (!shouldFilter) {
            // If neither mobile nor wifi is configured to filter, close tunnel temporarily
            closeInterface()
            updateNotificationText("Filtro pausado na rede atual")
            return
        }

        val activePkg = TrafficDirectorManager.currentForegroundPackage.value ?: packageName
        val activeAppName = TrafficDirectorManager.currentForegroundAppName.value
        val whitelist = repo.whitelistedPackages.value

        try {
            val builder = Builder()
                .setSession("FocusNet Escudo Ativo")
                .addAddress("10.120.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)

            // Direct internet access granted to foreground app:
            try {
                builder.addDisallowedApplication(activePkg)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Active package not found: $activePkg")
            }

            // Direct internet access granted to whitelisted background apps:
            for (pkg in whitelist) {
                if (pkg != activePkg) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                        // ignore uninstalled
                    }
                }
            }

            // Direct internet access for FocusNet itself
            if (activePkg != packageName && !whitelist.contains(packageName)) {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    // ignore
                }
            }

            // Close previous interface safely
            closeInterface()

            vpnInterface = builder.establish()

            updateNotificationText("Liberado para $activeAppName. Background bloqueado.")

            repo.addLog(
                TrafficLogEntry(
                    packageName = activePkg,
                    appName = activeAppName,
                    eventType = TrafficEventType.FOREGROUND_GRANTED,
                    detail = "Firewall reconfigurado: $activeAppName está com acesso liberado. Apps em 2º plano bloqueados."
                )
            )

            startDrainingPackets()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            repo.addLog(
                TrafficLogEntry(
                    packageName = activePkg,
                    appName = activeAppName,
                    eventType = TrafficEventType.BACKGROUND_BLOCKED,
                    detail = "Erro ao reconfigurar interface do firewall: ${e.message}"
                )
            )
        }
    }

    private fun startDrainingPackets() {
        packetDrainJob?.cancel()
        val pfd = vpnInterface ?: return

        packetDrainJob = serviceScope.launch {
            val inStream = FileInputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32768)
            val repo = FocusNetRepository.getInstance(applicationContext)

            try {
                while (isActive && vpnInterface != null) {
                    val length = inStream.read(buffer)
                    if (length > 0) {
                        // Packet captured from an unpermitted background app trying to access the internet!
                        // Drop it silently to save data and enforce isolation.
                        repo.incrementBlockedCount()
                    }
                }
            } catch (e: IOException) {
                // Expected when interface is closed
            }
        }
    }

    private fun updateNotificationText(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FocusNet: Internet em Primeiro Plano")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Pausar",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, FocusNetVpnService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun closeInterface() {
        packetDrainJob?.cancel()
        packetDrainJob = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
    }

    private fun stopVpn() {
        closeInterface()
        TrafficDirectorManager.unregisterVpnService()
        val repo = FocusNetRepository.getInstance(applicationContext)
        repo.updateProtection(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        closeInterface()
        TrafficDirectorManager.unregisterVpnService()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_RECONFIGURE = "com.example.action.RECONFIGURE"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "focusnet_status_channel"
        private const val TAG = "FocusNetVpnService"
    }
}
