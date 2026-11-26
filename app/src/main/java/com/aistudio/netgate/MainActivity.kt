package com.aistudio.netgate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.service.BackgroundPowerHelper
import com.aistudio.netgate.service.NetGateVpnService
import com.aistudio.netgate.service.NetworkMonitorService
import com.aistudio.netgate.service.SystemNetworkMonitor
import com.aistudio.netgate.service.vpn.TunnelWatchdog
import com.aistudio.netgate.ui.FirewallScreen
import com.aistudio.netgate.ui.FirewallViewModel
import com.aistudio.netgate.ui.components.GlassToastHost
import com.aistudio.netgate.ui.components.StartupSplashOverlay
import com.aistudio.netgate.ui.theme.NetGateTheme

class MainActivity : ComponentActivity() {

    private var pendingDestination by mutableStateOf<AppDestination?>(null)
    private var pendingVpnConsentRequest by mutableStateOf(false)
    private var latestExcludeFromRecents by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the kill switch, auto-reconnect and trusted Wi-Fi automation alive for the whole app.
        TunnelWatchdog.ensureStarted(applicationContext)
        pendingDestination = parseDestinationExtra(intent)
        pendingVpnConsentRequest = intent?.getBooleanExtra(EXTRA_REQUEST_VPN_CONSENT, false) == true

        setContent {
            val viewModel: FirewallViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(pendingDestination) {
                pendingDestination?.let { dest ->
                    viewModel.selectDestination(dest)
                    pendingDestination = null
                }
            }

            LaunchedEffect(uiState.preferences.excludeFromRecents) {
                latestExcludeFromRecents = uiState.preferences.excludeFromRecents
                BackgroundPowerHelper.applyExcludeFromRecents(
                    this@MainActivity,
                    uiState.preferences.excludeFromRecents
                )
            }

            NetGateTheme(
                themeMode = uiState.preferences.themeMode,
                themeStyle = uiState.preferences.themeStyle,
                dynamicColor = uiState.preferences.useDynamicColor,
                blurEngineStyle = uiState.preferences.blurEngineStyle,
                blurIntensity = uiState.preferences.blurIntensity,
                blurAnimationEnabled = uiState.preferences.isBlurAnimationEnabled
            ) {
                val activeDarkTheme = com.aistudio.netgate.ui.theme.LocalIsDarkTheme.current
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ) { activeDarkTheme },
                        navigationBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ) { activeDarkTheme }
                    )
                }
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

                LaunchedEffect(pendingVpnConsentRequest) {
                    if (pendingVpnConsentRequest) {
                        pendingVpnConsentRequest = false
                        requestEnableFirewall()
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

                var showStartupAnimation by rememberSaveable { mutableStateOf(true) }
                var startupReplayNonce by remember { mutableIntStateOf(0) }
                val activeThemeStyle = uiState.preferences.themeStyle
                val contentScale by animateFloatAsState(
                    targetValue = if (showStartupAnimation) 0.965f else 1f,
                    animationSpec = if (activeThemeStyle == NetGateThemeStyle.LIQUID_GLASS ||
                        activeThemeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE
                    ) {
                        spring(
                            dampingRatio = activeThemeStyle.springDampingRatio,
                            stiffness = activeThemeStyle.springStiffness
                        )
                    } else {
                        tween(durationMillis = 360, easing = FastOutSlowInEasing)
                    },
                    label = "startupContentRevealScale"
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    FirewallScreen(
                        viewModel = viewModel,
                        onRequestEnableFirewall = requestEnableFirewall,
                        onRequestToggleStatusBarSpeed = requestToggleStatusBarSpeed,
                        onReplayStartupAnimation = {
                            startupReplayNonce += 1
                            showStartupAnimation = true
                        },
                        modifier = Modifier.graphicsLayer {
                            scaleX = contentScale
                            scaleY = contentScale
                        }
                    )

                    GlassToastHost()

                    if (showStartupAnimation) {
                        key(startupReplayNonce) {
                            StartupSplashOverlay(
                                isFirewallActive = uiState.preferences.isFirewallEnabled &&
                                    uiState.vpnRuntimeState.isServiceRunning,
                                onDismissed = { showStartupAnimation = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BackgroundPowerHelper.applyExcludeFromRecents(this, latestExcludeFromRecents)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDestinationExtra(intent)?.let { dest ->
            pendingDestination = dest
        }
        if (intent.getBooleanExtra(EXTRA_REQUEST_VPN_CONSENT, false)) {
            pendingVpnConsentRequest = true
        }
    }

    private fun parseDestinationExtra(intent: Intent?): AppDestination? {
        val raw = intent?.getStringExtra(EXTRA_DESTINATION) ?: return null
        return AppDestination.entries.firstOrNull { it.name == raw }
    }

    companion object {
        const val EXTRA_DESTINATION = "extra_destination"
        const val EXTRA_REQUEST_VPN_CONSENT = "extra_request_vpn_consent"
    }
}
