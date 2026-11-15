package com.example

import android.Manifest
import android.app.Activity
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.NetGateVpnService
import com.example.ui.FirewallScreen
import com.example.ui.FirewallViewModel
import com.example.ui.theme.NetGateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: FirewallViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

                LaunchedEffect(uiState.preferences.isFirewallEnabled) {
                    if (uiState.preferences.isFirewallEnabled &&
                        !NetGateVpnService.runtimeState.value.isServiceRunning &&
                        VpnService.prepare(this@MainActivity) == null
                    ) {
                        NetGateVpnService.startOrReload(this@MainActivity)
                    }
                }

                FirewallScreen(
                    viewModel = viewModel,
                    onRequestEnableFirewall = requestEnableFirewall
                )
            }
        }
    }
}
