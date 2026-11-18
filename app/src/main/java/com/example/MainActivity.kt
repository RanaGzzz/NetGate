package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppDestination
import com.example.service.NetGateVpnService
import com.example.service.NetworkMonitorService
import com.example.service.SystemNetworkMonitor
import com.example.ui.FirewallScreen
import com.example.ui.FirewallViewModel
import com.example.ui.theme.NetGateTheme

class MainActivity : ComponentActivity() {

    private var pendingDestination by mutableStateOf<AppDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDestination = parseDestinationExtra(intent)

        setContent {
            val viewModel: FirewallViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(pendingDestination) {
                pendingDestination?.let { dest ->
                    viewModel.selectDestination(dest)
                    pendingDestination = null
                }
            }

            NetGateTheme(
                themeMode = uiState.preferences.themeMode,
                dynamicColor = uiState.preferences.useDynamicColor
            ) {
                val vpnConsentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        viewModel.onFirewallPermissionGranted()
                    } else {
                        viewModel.onVpnPermissionDenied()
                    }
                }

                val launchVpnConsentOrStart: () -> Unit = {
                    val prepareIntent = VpnService.prepare(this@MainActivity)
                    if (prepareIntent == null) {
                        viewModel.onFirewallPermissionGranted()
                    } else {
                        vpnConsentLauncher.launch(prepareIntent)
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    launchVpnConsentOrStart()
                }

                val statusBarNotifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.setStatusBarSpeedEnabled(true)
                    } else {
                        viewModel.onStatusBarSpeedPermissionDenied()
                    }
                }

                val requestEnableFirewall: () -> Unit = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            launchVpnConsentOrStart()
                        }
                    } else {
                        launchVpnConsentOrStart()
                    }
                }

                val requestToggleStatusBarSpeed: (Boolean) -> Unit = { enable ->
                    if (!enable) {
                        viewModel.setStatusBarSpeedEnabled(false)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            statusBarNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setStatusBarSpeedEnabled(true)
                        }
                    } else {
                        viewModel.setStatusBarSpeedEnabled(true)
                    }
                }

                LaunchedEffect(uiState.preferences.isFirewallEnabled) {
                    if (uiState.preferences.isFirewallEnabled &&
                        !NetGateVpnService.runtimeState.value.isServiceRunning &&
                        VpnService.prepare(this@MainActivity) == null
                    ) {
                        NetGateVpnService.startOrReload(this@MainActivity)
                    }
                }

                LaunchedEffect(uiState.preferences.isStatusBarSpeedEnabled) {
                    if (uiState.preferences.isStatusBarSpeedEnabled && !SystemNetworkMonitor.isServiceActive) {
                        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        if (canNotify) {
                            NetworkMonitorService.start(this@MainActivity)
                        }
                    }
                }

                FirewallScreen(
                    viewModel = viewModel,
                    onRequestEnableFirewall = requestEnableFirewall,
                    onRequestToggleStatusBarSpeed = requestToggleStatusBarSpeed
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDestinationExtra(intent)?.let { dest ->
            pendingDestination = dest
        }
    }

    private fun parseDestinationExtra(intent: Intent?): AppDestination? {
        val raw = intent?.getStringExtra(EXTRA_DESTINATION) ?: return null
        return AppDestination.entries.firstOrNull { it.name == raw }
    }

    companion object {
        const val EXTRA_DESTINATION = "extra_destination"
    }
}
