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
import com.example.data.model.BlurEngineStyle
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.SniProxyPreset
import com.example.data.model.StatusBarSpeedStyle
import com.example.data.model.ThemeMode
import com.example.data.model.UpstreamDnsServer
import com.example.data.model.WebsiteFilterMode
import com.example.data.model.YouTubeRestrictedMode
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
        val IS_SAFE_SEARCH_ENABLED = booleanPreferencesKey("is_safe_search_enabled")
        val YOUTUBE_RESTRICTED_MODE = stringPreferencesKey("youtube_restricted_mode")
        val IS_YOUTUBE_AD_BLOCK_ENABLED = booleanPreferencesKey("is_youtube_ad_block_enabled")
        val SNI_PROXY_PRESET = stringPreferencesKey("sni_proxy_preset")
        val CUSTOM_SNI_PROXY_HOST = stringPreferencesKey("custom_sni_proxy_host")
        val CUSTOM_SNI_PROXY_PORT = intPreferencesKey("custom_sni_proxy_port")
        val IS_STATUS_BAR_SPEED_ENABLED = booleanPreferencesKey("is_status_bar_speed_enabled")
        val STATUS_BAR_SPEED_STYLE = stringPreferencesKey("status_bar_speed_style")
        val SHOW_STATUS_BAR_DAILY_USAGE = booleanPreferencesKey("show_status_bar_daily_usage")
        val IS_DEEP_MONITOR_ENABLED = booleanPreferencesKey("is_deep_monitor_enabled")
        val BLUR_ENGINE_STYLE = stringPreferencesKey("blur_engine_style")
        val IS_BLUR_ANIMATION_ENABLED = booleanPreferencesKey("is_blur_animation_enabled")
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
        val youtubeModeName = prefs[Keys.YOUTUBE_RESTRICTED_MODE] ?: YouTubeRestrictedMode.OFF.name
        val youtubeMode = YouTubeRestrictedMode.entries.firstOrNull { it.name == youtubeModeName }
            ?: YouTubeRestrictedMode.OFF
        val sniPresetName = prefs[Keys.SNI_PROXY_PRESET] ?: SniProxyPreset.STANDARD_DIRECT.name
        val sniPreset = SniProxyPreset.entries.firstOrNull { it.name == sniPresetName }
            ?: SniProxyPreset.STANDARD_DIRECT
        val statusBarStyleName = prefs[Keys.STATUS_BAR_SPEED_STYLE] ?: StatusBarSpeedStyle.DOWNLOAD_UPLOAD.name
        val statusBarStyle = StatusBarSpeedStyle.entries.firstOrNull { it.name == statusBarStyleName }
            ?: StatusBarSpeedStyle.DOWNLOAD_UPLOAD
        val blurStyleName = prefs[Keys.BLUR_ENGINE_STYLE] ?: BlurEngineStyle.ACRYLIC_PRO.name
        val blurStyle = BlurEngineStyle.entries.firstOrNull { it.name == blurStyleName }
            ?: BlurEngineStyle.ACRYLIC_PRO

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
            isProxyStripTrackingParams = prefs[Keys.IS_PROXY_STRIP_TRACKING_PARAMS] ?: true,
            isSafeSearchEnabled = prefs[Keys.IS_SAFE_SEARCH_ENABLED] ?: false,
            youtubeRestrictedMode = youtubeMode,
            isYoutubeAdBlockEnabled = prefs[Keys.IS_YOUTUBE_AD_BLOCK_ENABLED] ?: false,
            sniProxyPreset = sniPreset,
            customSniProxyHost = prefs[Keys.CUSTOM_SNI_PROXY_HOST] ?: "",
            customSniProxyPort = (prefs[Keys.CUSTOM_SNI_PROXY_PORT] ?: 8080).coerceIn(1, 65535),
            isStatusBarSpeedEnabled = prefs[Keys.IS_STATUS_BAR_SPEED_ENABLED] ?: false,
            statusBarSpeedStyle = statusBarStyle,
            showStatusBarDailyUsage = prefs[Keys.SHOW_STATUS_BAR_DAILY_USAGE] ?: true,
            isDeepMonitorEnabled = prefs[Keys.IS_DEEP_MONITOR_ENABLED] ?: true,
            blurEngineStyle = blurStyle,
            isBlurAnimationEnabled = prefs[Keys.IS_BLUR_ANIMATION_ENABLED] ?: true
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

    suspend fun setSafeSearchEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_SAFE_SEARCH_ENABLED] = enabled }
    }

    suspend fun setYoutubeRestrictedMode(mode: YouTubeRestrictedMode) {
        context.firewallDataStore.edit { it[Keys.YOUTUBE_RESTRICTED_MODE] = mode.name }
    }

    suspend fun setYoutubeAdBlockEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_YOUTUBE_AD_BLOCK_ENABLED] = enabled }
    }

    suspend fun applySniProxyPreset(preset: SniProxyPreset) {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.SNI_PROXY_PRESET] = preset.name
            prefs[Keys.IS_HTTPS_PROXY_ENABLED] = true
            prefs[Keys.IS_PROXY_STRIP_TRACKING_PARAMS] = preset.defaultStripTracking
            if (preset.defaultAdBlocker) {
                prefs[Keys.IS_AD_BLOCKER_ENABLED] = true
            }
            if (preset.defaultYoutubeAdBlock) {
                prefs[Keys.IS_YOUTUBE_AD_BLOCK_ENABLED] = true
            }
            if (preset.defaultSafeSearch) {
                prefs[Keys.IS_SAFE_SEARCH_ENABLED] = true
            }
            preset.defaultYoutubeMode?.let { mode ->
                prefs[Keys.YOUTUBE_RESTRICTED_MODE] = mode.name
            }
        }
    }

    suspend fun setCustomSniProxy(host: String, port: Int) {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_SNI_PROXY_HOST] = host.trim()
            prefs[Keys.CUSTOM_SNI_PROXY_PORT] = port.coerceIn(1, 65535)
            prefs[Keys.SNI_PROXY_PRESET] = SniProxyPreset.CUSTOM_UPSTREAM_SNI.name
            prefs[Keys.IS_HTTPS_PROXY_ENABLED] = true
        }
    }

    suspend fun setStatusBarSpeedEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_STATUS_BAR_SPEED_ENABLED] = enabled }
    }

    suspend fun setStatusBarSpeedStyle(style: StatusBarSpeedStyle) {
        context.firewallDataStore.edit { it[Keys.STATUS_BAR_SPEED_STYLE] = style.name }
    }

    suspend fun setShowStatusBarDailyUsage(show: Boolean) {
        context.firewallDataStore.edit { it[Keys.SHOW_STATUS_BAR_DAILY_USAGE] = show }
    }

    suspend fun setDeepMonitorEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_DEEP_MONITOR_ENABLED] = enabled }
    }

    suspend fun setBlurEngineStyle(style: BlurEngineStyle) {
        context.firewallDataStore.edit { it[Keys.BLUR_ENGINE_STYLE] = style.name }
    }

    suspend fun setBlurAnimationEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.IS_BLUR_ANIMATION_ENABLED] = enabled }
    }
}
