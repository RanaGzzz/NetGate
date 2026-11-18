package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.ThemeMode
import com.example.data.model.UpstreamDnsServer
import com.example.data.model.WebsiteFilterMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.firewallDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "netgate_preferences"
)

class FirewallPreferences(private val context: Context) {

    private object Keys {
        val IS_FIREWALL_ENABLED = booleanPreferencesKey("is_firewall_enabled")
        val SHOW_OFFLINE_APPS = booleanPreferencesKey("show_offline_apps")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val DEFAULT_BLOCK_WIFI = booleanPreferencesKey("default_block_wifi")
        val DEFAULT_BLOCK_DATA = booleanPreferencesKey("default_block_data")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val WEBSITE_FILTER_MODE = stringPreferencesKey("website_filter_mode")
        val WEBSITE_BYPASS_PACKAGES = stringSetPreferencesKey("website_bypass_packages")
        val IS_AD_BLOCKER_ENABLED = booleanPreferencesKey("is_ad_blocker_enabled")
        val IS_ESSENTIAL_PASSTHROUGH_ENABLED = booleanPreferencesKey("is_essential_passthrough_enabled")
        val USE_DOH_UPSTREAM = booleanPreferencesKey("use_doh_upstream")
        val LIFETIME_ADS_BLOCKED_COUNT = longPreferencesKey("lifetime_ads_blocked_count")
        val SELECTED_UPSTREAM_DNS = stringPreferencesKey("selected_upstream_dns")
        val CUSTOM_DOH_URL = stringPreferencesKey("custom_doh_url")
        val CUSTOM_UDP_DNS_IP = stringPreferencesKey("custom_udp_dns_ip")
        val IS_HTTPS_PROXY_ENABLED = booleanPreferencesKey("is_https_proxy_enabled")
        val HTTPS_PROXY_PORT = intPreferencesKey("https_proxy_port")
        val IS_PROXY_STRIP_TRACKING_PARAMS = booleanPreferencesKey("is_proxy_strip_tracking_params")
    }

    val preferencesFlow: Flow<FirewallPreferencesState> = context.firewallDataStore.data.map { prefs ->
        val themeModeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeModeName } ?: ThemeMode.SYSTEM
        val websiteModeName = prefs[Keys.WEBSITE_FILTER_MODE] ?: WebsiteFilterMode.OFF.name
        val websiteMode = WebsiteFilterMode.entries.firstOrNull { it.name == websiteModeName }
            ?: WebsiteFilterMode.OFF
        val upstreamName = prefs[Keys.SELECTED_UPSTREAM_DNS] ?: UpstreamDnsServer.CLOUDFLARE_STANDARD.name
        val upstreamDns = UpstreamDnsServer.entries.firstOrNull { it.name == upstreamName }
            ?: UpstreamDnsServer.CLOUDFLARE_STANDARD

        FirewallPreferencesState(
            isFirewallEnabled = prefs[Keys.IS_FIREWALL_ENABLED] ?: false,
            showOfflineApps = prefs[Keys.SHOW_OFFLINE_APPS] ?: false,
            showSystemApps = prefs[Keys.SHOW_SYSTEM_APPS] ?: true,
            defaultBlockWifiForNewRules = prefs[Keys.DEFAULT_BLOCK_WIFI] ?: false,
            defaultBlockDataForNewRules = prefs[Keys.DEFAULT_BLOCK_DATA] ?: false,
            themeMode = themeMode,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
            websiteFilterMode = websiteMode,
            websiteBypassPackages = prefs[Keys.WEBSITE_BYPASS_PACKAGES] ?: emptySet(),
            isAdBlockerEnabled = prefs[Keys.IS_AD_BLOCKER_ENABLED] ?: false,
            isEssentialPassthroughEnabled = prefs[Keys.IS_ESSENTIAL_PASSTHROUGH_ENABLED] ?: true,
            useDohUpstream = prefs[Keys.USE_DOH_UPSTREAM] ?: true,
            lifetimeAdsBlockedCount = prefs[Keys.LIFETIME_ADS_BLOCKED_COUNT] ?: 0L,
            selectedUpstreamDns = upstreamDns,
            customDohUrl = prefs[Keys.CUSTOM_DOH_URL] ?: "",
            customUdpDnsIp = prefs[Keys.CUSTOM_UDP_DNS_IP] ?: "",
            isHttpsProxyEnabled = prefs[Keys.IS_HTTPS_PROXY_ENABLED] ?: false,
            httpsProxyPort = (prefs[Keys.HTTPS_PROXY_PORT] ?: 8899).coerceIn(1024, 65535),
            isProxyStripTrackingParams = prefs[Keys.IS_PROXY_STRIP_TRACKING_PARAMS] ?: true
        )
    }

    suspend fun setFirewallEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_FIREWALL_ENABLED] = enabled }
    }

    suspend fun setShowOfflineApps(show: Boolean) {
        context.firewallDataStore.edit { it[Keys.SHOW_OFFLINE_APPS] = show }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        context.firewallDataStore.edit { it[Keys.SHOW_SYSTEM_APPS] = show }
    }

    suspend fun setDefaultBlockWifi(block: Boolean) {
        context.firewallDataStore.edit { it[Keys.DEFAULT_BLOCK_WIFI] = block }
    }

    suspend fun setDefaultBlockData(block: Boolean) {
        context.firewallDataStore.edit { it[Keys.DEFAULT_BLOCK_DATA] = block }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.firewallDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setWebsiteFilterMode(mode: WebsiteFilterMode) {
        context.firewallDataStore.edit { it[Keys.WEBSITE_FILTER_MODE] = mode.name }
    }

    suspend fun toggleWebsiteBypassPackage(packageName: String) {
        context.firewallDataStore.edit { prefs ->
            val current = prefs[Keys.WEBSITE_BYPASS_PACKAGES] ?: emptySet()
            prefs[Keys.WEBSITE_BYPASS_PACKAGES] = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        }
    }

    suspend fun clearWebsiteBypassPackages() {
        context.firewallDataStore.edit { it[Keys.WEBSITE_BYPASS_PACKAGES] = emptySet() }
    }

    suspend fun setAdBlockerEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_AD_BLOCKER_ENABLED] = enabled }
    }

    suspend fun setEssentialPassthroughEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_ESSENTIAL_PASSTHROUGH_ENABLED] = enabled }
    }

    suspend fun setUseDohUpstream(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.USE_DOH_UPSTREAM] = enabled }
    }

    suspend fun incrementLifetimeAdsBlocked(delta: Long = 1L) {
        if (delta <= 0L) return
        context.firewallDataStore.edit { prefs ->
            val current = prefs[Keys.LIFETIME_ADS_BLOCKED_COUNT] ?: 0L
            prefs[Keys.LIFETIME_ADS_BLOCKED_COUNT] = current + delta
        }
    }

    suspend fun setSelectedUpstreamDns(server: UpstreamDnsServer) {
        context.firewallDataStore.edit { it[Keys.SELECTED_UPSTREAM_DNS] = server.name }
    }

    suspend fun setCustomDnsEndpoints(dohUrl: String, udpIp: String) {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_DOH_URL] = dohUrl.trim()
            prefs[Keys.CUSTOM_UDP_DNS_IP] = udpIp.trim()
        }
    }

    suspend fun setHttpsProxyEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_HTTPS_PROXY_ENABLED] = enabled }
    }

    suspend fun setHttpsProxyPort(port: Int) {
        context.firewallDataStore.edit { it[Keys.HTTPS_PROXY_PORT] = port.coerceIn(1024, 65535) }
    }

    suspend fun setProxyStripTrackingParams(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_PROXY_STRIP_TRACKING_PARAMS] = enabled }
    }
}
