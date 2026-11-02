package com.example

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.BlockedBackgroundScreen
import com.example.ui.screens.WhitelistScreen
import com.example.ui.theme.DarkEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusNetTheme
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateBackgroundDark
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateSurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FocusNetTheme(darkTheme = true) {
                FocusNetApp()
            }
        }
    }
}

@Composable
fun FocusNetApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsState()
    val apps by viewModel.filteredApps.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val blockedAttempts by viewModel.blockedAttempts.collectAsState()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val switchesCount by viewModel.switchesCount.collectAsState()
    val connectionType by viewModel.connectionType.collectAsState()
    val currentAppName by viewModel.currentForegroundAppName.collectAsState()
    val currentPackageName by viewModel.currentForegroundPackage.collectAsState()
    val activeGracePasses by viewModel.activeGracePasses.collectAsState()
    val temporaryPasses by viewModel.temporaryPasses.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityPermissionGranted.collectAsState()
    val isVpnPrepared by viewModel.isVpnPermissionGranted.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    // VPN permission launcher
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshPermissionStatuses()
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleProtection(true)
        }
    }

    // Refresh permission status when activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestVpn = {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            viewModel.toggleProtection(true)
        }
    }

    val openAccessibility = {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SlateBackgroundDark,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            FocusNetTopBar(
                isProtectionActive = settings.isProtectionEnabled,
                currentAppName = currentAppName
            )
        },
        bottomBar = {
            FocusNetBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.setSelectedTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    settings = settings,
                    currentAppName = currentAppName,
                    currentPackageName = currentPackageName,
                    connectionType = connectionType,
                    activeGracePasses = activeGracePasses,
                    temporaryPasses = temporaryPasses,
                    onGetAppName = { viewModel.getAppName(it) },
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isVpnPrepared = isVpnPrepared,
                    blockedCount = blockedCount,
                    switchesCount = switchesCount,
                    onToggleProtection = { enable ->
                        if (enable) {
                            val vpnIntent = VpnService.prepare(context)
                            if (vpnIntent != null) {
                                vpnLauncher.launch(vpnIntent)
                            } else {
                                viewModel.toggleProtection(true)
                            }
                        } else {
                            viewModel.toggleProtection(false)
                        }
                    },
                    onToggleAutoActivate = { viewModel.toggleAutoActivate(it) },
                    onToggleMobileFilter = { viewModel.toggleMobileFilter(it) },
                    onToggleWifiFilter = { viewModel.toggleWifiFilter(it) },
                    onUpdateGracePeriodSeconds = { viewModel.updateGracePeriodSeconds(it) },
                    onRevokeTemporaryPass = { viewModel.revokeTemporaryPass(it) },
                    onRequestVpnPermission = requestVpn,
                    onOpenAccessibilitySettings = openAccessibility,
                    onSimulateAppSwitch = { viewModel.simulateAppSwitch(it) },
                    onNavigateToWhitelist = { viewModel.setSelectedTab(1) },
                    onNavigateToBlocked = { viewModel.setSelectedTab(3) }
                )
                1 -> WhitelistScreen(
                    apps = apps,
                    searchQuery = searchQuery,
                    temporaryPasses = temporaryPasses,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onToggleWhitelist = { pkg, allowed -> viewModel.toggleAppWhitelist(pkg, allowed) },
                    onGrantTemporaryPass = { pkg, mins -> viewModel.grantTemporaryPass(pkg, mins) },
                    onRevokeTemporaryPass = { pkg -> viewModel.revokeTemporaryPass(pkg) }
                )
                2 -> LogsScreen(
                    logs = logs,
                    onClearLogs = { viewModel.clearLogs() }
                )
                3 -> BlockedBackgroundScreen(
                    blockedAttempts = blockedAttempts,
                    onClearBlocked = { viewModel.clearBlockedAttempts() }
                )
            }
        }
    }
}

@Composable
fun FocusNetTopBar(
    isProtectionActive: Boolean,
    currentAppName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SlateSurfaceDark)
            .border(width = 0.5.dp, color = SlateCardBorder)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFF0F261C), CircleShape)
                        .border(1.dp, NeonEmerald.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "FocusNet Logo",
                        tint = NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "FocusNet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Direcionador de Tráfego",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(
                        color = if (isProtectionActive) Color(0xFF064E3B) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isProtectionActive) NeonEmerald.copy(alpha = 0.5f) else SlateCardBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (isProtectionActive) NeonEmerald else Color(0xFF64748B),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isProtectionActive) "PROTEGENDO: $currentAppName" else "DESATIVADO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isProtectionActive) NeonEmerald else TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FocusNetBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = SlateSurfaceDark,
        tonalElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .testTag("bottom_nav_bar")
    ) {
        val items = listOf(
            Triple("Escudo", Icons.Filled.Shield, Icons.Outlined.Shield),
            Triple("Exceções", Icons.Filled.DataSaverOn, Icons.Outlined.DataSaverOn),
            Triple("Histórico", Icons.Filled.History, Icons.Outlined.History),
            Triple("2º Plano", Icons.Filled.SignalWifiOff, Icons.Outlined.SignalWifiOff)
        )

        items.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
            val isSelected = selectedTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) filledIcon else outlinedIcon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = NeonEmerald,
                    indicatorColor = NeonEmerald,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag("nav_item_$index")
            )
        }
    }
}
