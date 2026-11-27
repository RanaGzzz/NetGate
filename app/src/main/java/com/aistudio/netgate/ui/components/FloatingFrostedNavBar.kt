package com.aistudio.netgate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.DataUsageSubTab

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
    val activeTabs = config.activeOrderedTabs

    val items = activeTabs.map { tabId ->
        when (tabId) {
            BottomNavTabId.FIREWALL -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "Firewall",
                testTag = tabId.testTag,
                onClick = onSelectFirewall
            )
            BottomNavTabId.AD_BLOCKER -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "Ad & Network Guard",
                testTag = tabId.testTag,
                onClick = onSelectWebsites
            )
            BottomNavTabId.SNI_DPI -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "SNI Proxy & DPI Bypass",
                testTag = tabId.testTag,
                onClick = onSelectSniDpi
            )
            BottomNavTabId.VPN -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "VPN Guard (WireGuard & OpenVPN)",
                testTag = tabId.testTag,
                onClick = onSelectVpnGuard
            )
            BottomNavTabId.DNS -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "1.1.1.1 DNS Protection",
                testTag = tabId.testTag,
                onClick = onSelectDnsProtection
            )
            BottomNavTabId.HOTSPOT -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "Hotspot & Wi-Fi",
                testTag = tabId.testTag,
                onClick = onSelectHotspot
            )
            BottomNavTabId.USAGE -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "Network & Data Usage",
                testTag = tabId.testTag,
                onClick = onSelectDataUsage
            )
            BottomNavTabId.WIFI -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "Wi-Fi Analysis & Tools",
                testTag = tabId.testTag,
                onClick = onSelectWifi
            )
            BottomNavTabId.SPEED_TEST -> GlassPillNavItem(
                icon = tabId.resolveIcon(),
                label = tabId.title,
                contentDescription = "Network Speed Test",
                testTag = tabId.testTag,
                onClick = onSelectSpeedTest
            )
        }
    }

    val activeTabId: BottomNavTabId = when {
        isSniDpiSelected -> BottomNavTabId.SNI_DPI
        currentDestination == AppDestination.FIREWALL -> BottomNavTabId.FIREWALL
        currentDestination == AppDestination.WEBSITES -> BottomNavTabId.AD_BLOCKER
        currentDestination == AppDestination.VPN_GUARD -> BottomNavTabId.VPN
        currentDestination == AppDestination.DNS -> BottomNavTabId.DNS
        currentDestination == AppDestination.HOTSPOT -> BottomNavTabId.HOTSPOT
        currentDestination == AppDestination.DATA_USAGE -> {
            when (currentDataUsageSubTab) {
                DataUsageSubTab.WIFI -> if (BottomNavTabId.WIFI in activeTabs) BottomNavTabId.WIFI else BottomNavTabId.USAGE
                DataUsageSubTab.SPEED_TEST -> if (BottomNavTabId.SPEED_TEST in activeTabs) BottomNavTabId.SPEED_TEST else BottomNavTabId.USAGE
                else -> BottomNavTabId.USAGE
            }
        }
        else -> BottomNavTabId.FIREWALL
    }
    val selectedIndex = activeTabs.indexOf(activeTabId).takeIf { it >= 0 } ?: -1

    GlassPillBottomNav(
        items = items,
        selectedIndex = selectedIndex,
        isDimmed = isConnectionInfoOpen,
        modifier = modifier
    )
}
