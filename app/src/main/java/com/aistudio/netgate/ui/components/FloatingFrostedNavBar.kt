package com.aistudio.netgate.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.DataUsageSubTab

@Composable
private fun BottomNavTabId.resolveDestinationAction(
    onSelectFirewall: () -> Unit,
    onSelectWebsites: () -> Unit,
    onSelectDataUsage: () -> Unit,
    onSelectDnsProtection: () -> Unit,
    onSelectHotspot: () -> Unit,
    onSelectVpnGuard: () -> Unit,
    onSelectSniDpi: () -> Unit,
    onSelectWifi: () -> Unit,
    onSelectSpeedTest: () -> Unit
): () -> Unit = when (this) {
    BottomNavTabId.FIREWALL -> onSelectFirewall
    BottomNavTabId.AD_BLOCKER -> onSelectWebsites
    BottomNavTabId.SNI_DPI -> onSelectSniDpi
    BottomNavTabId.VPN -> onSelectVpnGuard
    BottomNavTabId.DNS -> onSelectDnsProtection
    BottomNavTabId.HOTSPOT -> onSelectHotspot
    BottomNavTabId.USAGE -> onSelectDataUsage
    BottomNavTabId.WIFI -> onSelectWifi
    BottomNavTabId.SPEED_TEST -> onSelectSpeedTest
}

private fun BottomNavTabId.contentDescriptionText(): String = when (this) {
    BottomNavTabId.FIREWALL -> "Firewall"
    BottomNavTabId.AD_BLOCKER -> "Ad & Network Guard"
    BottomNavTabId.SNI_DPI -> "SNI Proxy & DPI Bypass"
    BottomNavTabId.VPN -> "VPN Guard (WireGuard & OpenVPN)"
    BottomNavTabId.DNS -> "1.1.1.1 DNS Protection"
    BottomNavTabId.HOTSPOT -> "Hotspot & Wi-Fi"
    BottomNavTabId.USAGE -> "Network & Data Usage"
    BottomNavTabId.WIFI -> "Wi-Fi Analysis & Tools"
    BottomNavTabId.SPEED_TEST -> "Network Speed Test"
}

@Composable
fun FloatingFrostedNavBar(
    currentDestination: AppDestination,
    isConnectionInfoOpen: Boolean,
    onSelectFirewall: () -> Unit,
    onSelectWebsites: () -> Unit,
    onSelectDataUsage: () -> Unit = {},
    onSelectDnsProtection: () -> Unit,
    onSelectHotspot: () -> Unit = {},
    onSelectVpnGuard: () -> Unit = {},
    isSniDpiSelected: Boolean = false,
    onSelectSniDpi: () -> Unit = {},
    currentDataUsageSubTab: DataUsageSubTab? = null,
    onSelectWifi: () -> Unit = {},
    onSelectSpeedTest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val config = LocalBottomNavConfig.current
    val visibleTabs = config.activeOrderedTabs

    val activeTabId: BottomNavTabId = when {
        isSniDpiSelected -> BottomNavTabId.SNI_DPI
        currentDestination == AppDestination.FIREWALL -> BottomNavTabId.FIREWALL
        currentDestination == AppDestination.WEBSITES -> BottomNavTabId.AD_BLOCKER
        currentDestination == AppDestination.VPN_GUARD -> BottomNavTabId.VPN
        currentDestination == AppDestination.DNS -> BottomNavTabId.DNS
        currentDestination == AppDestination.HOTSPOT -> BottomNavTabId.HOTSPOT
        currentDestination == AppDestination.DATA_USAGE -> {
            when (currentDataUsageSubTab) {
                DataUsageSubTab.WIFI -> BottomNavTabId.WIFI
                DataUsageSubTab.SPEED_TEST -> BottomNavTabId.SPEED_TEST
                else -> BottomNavTabId.USAGE
            }
        }
        else -> BottomNavTabId.FIREWALL
    }

    val items = visibleTabs.map { tabId ->
        GlassTabItem(
            activeIcon = tabId.resolveIcon(),
            label = tabId.title,
            contentDescription = tabId.contentDescriptionText(),
            testTag = tabId.testTag,
            onClick = tabId.resolveDestinationAction(
                onSelectFirewall = onSelectFirewall,
                onSelectWebsites = onSelectWebsites,
                onSelectDataUsage = onSelectDataUsage,
                onSelectDnsProtection = onSelectDnsProtection,
                onSelectHotspot = onSelectHotspot,
                onSelectVpnGuard = onSelectVpnGuard,
                onSelectSniDpi = onSelectSniDpi,
                onSelectWifi = onSelectWifi,
                onSelectSpeedTest = onSelectSpeedTest
            )
        )
    }

    val selectedIndex = visibleTabs.indexOf(activeTabId)

    GlassTabs(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = {},
        variant = GlassTabVariant.BOTTOM_NAV,
        isDimmed = isConnectionInfoOpen,
        modifier = modifier.fillMaxWidth()
    )
}
