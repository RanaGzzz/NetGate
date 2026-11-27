package com.aistudio.netgate.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.aistudio.netgate.R
import com.aistudio.netgate.data.model.AppDestination

@Suppress("UNUSED_PARAMETER")
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
    modifier: Modifier = Modifier
) {
    val vpnTunnelIcon = ImageVector.vectorResource(id = R.drawable.ic_vpn_tunnel_shield)
    val items = listOf(
        GlassPillNavItem(
            icon = Icons.Rounded.Shield,
            label = "Firewall",
            contentDescription = "Firewall",
            testTag = "nav_tab_firewall",
            onClick = onSelectFirewall
        ),
        GlassPillNavItem(
            icon = Icons.Rounded.Public,
            label = "Ad Blocker",
            contentDescription = "Ad & Network Guard",
            testTag = "nav_tab_websites",
            onClick = onSelectWebsites
        ),
        GlassPillNavItem(
            icon = Icons.Rounded.Settings,
            label = "SNI & DPI",
            contentDescription = "SNI Proxy & DPI Bypass",
            testTag = "nav_tab_sni_dpi",
            onClick = onSelectSniDpi
        ),
        GlassPillNavItem(
            icon = vpnTunnelIcon,
            label = "VPN",
            contentDescription = "VPN Guard (WireGuard & OpenVPN)",
            testTag = "floating_nav_vpn_guard",
            onClick = onSelectVpnGuard
        ),
        GlassPillNavItem(
            icon = Icons.Rounded.Dns,
            label = "DNS",
            contentDescription = "1.1.1.1 DNS Protection",
            testTag = "floating_nav_dns",
            onClick = onSelectDnsProtection
        ),
        GlassPillNavItem(
            icon = Icons.Rounded.WifiTethering,
            label = "Hotspot",
            contentDescription = "Hotspot & Wi-Fi",
            testTag = "floating_nav_hotspot",
            onClick = onSelectHotspot
        ),
        GlassPillNavItem(
            icon = Icons.Rounded.BarChart,
            label = "Usage",
            contentDescription = "Network & Data Usage",
            testTag = "nav_tab_data_usage",
            onClick = onSelectDataUsage
        )
    )
    val selectedIndex = if (isSniDpiSelected) {
        2
    } else {
        when (currentDestination) {
            AppDestination.FIREWALL -> 0
            AppDestination.WEBSITES -> 1
            AppDestination.VPN_GUARD -> 3
            AppDestination.DNS -> 4
            AppDestination.HOTSPOT -> 5
            AppDestination.DATA_USAGE -> 6
        }
    }

    GlassPillBottomNav(
        items = items,
        selectedIndex = selectedIndex,
        isDimmed = isConnectionInfoOpen,
        modifier = modifier
    )
}
