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
import com.example.data.model.AppIconStyle
import com.example.data.model.BackgroundAnimationStyle
import com.example.data.model.BlurEngineStyle
import com.example.data.model.DnsTransportMode
import com.example.data.model.DpiBypassMode
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.NetGateThemeStyle
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
        val THEME_STYLE = stringPreferencesKey("theme_style")
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
        val BACKGROUND_ANIMATION = stringPreferencesKey("background_animation")
        val BACKGROUND_PARALLAX_ENABLED = booleanPreferencesKey("background_parallax_enabled")
        val BACKGROUND_SEASONAL_ENABLED = booleanPreferencesKey("background_seasonal_enabled")
        val APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
        val HOTSPOT_PROXY_ENABLED = booleanPreferencesKey("hotspot_proxy_enabled")
        val HOTSPOT_PROXY_PORT = intPreferencesKey("hotspot_proxy_port")
        val HOTSPOT_APPLY_NETGATE_FILTERS = booleanPreferencesKey("hotspot_apply_netgate_filters")
        val HOTSPOT_BLOCKED_CLIENT_IPS = stringSetPreferencesKey("hotspot_blocked_client_ips")
        val HOTSPOT_CLIENT_CAPS_SET = stringSetPreferencesKey("hotspot_client_caps_set")
        val HOTSPOT_CLIENT_LABELS_SET = stringSetPreferencesKey("hotspot_client_labels_set")
        val GLOBAL_WIFI_BLOCKED = booleanPreferencesKey("global_wifi_blocked")
        val GLOBAL_WIFI_TIMER_EXPIRES_AT = longPreferencesKey("global_wifi_timer_expires_at")
        val GLOBAL_WIFI_TIMER_DURATION_MS = longPreferencesKey("global_wifi_timer_duration_ms")
        val AUTO_BLOCK_WIFI_ON_IDLE = booleanPreferencesKey("auto_block_wifi_on_idle")
        val AUTO_BLOCK_WIFI_IDLE_MINUTES = intPreferencesKey("auto_block_wifi_idle_minutes")
        val AUTO_BLOCK_WIFI_PANEL_NOTIFY = booleanPreferencesKey("auto_block_wifi_panel_notify")
        val DPI_BYPASS_MODE = stringPreferencesKey("dpi_bypass_mode")
        val DPI_FRAGMENT_CHUNK_SIZE = intPreferencesKey("dpi_fragment_chunk_size")
        val DPI_FRAGMENT_DELAY_MS = intPreferencesKey("dpi_fragment_delay_ms")
        val DPI_IDLE_KEEPALIVE_ENABLED = booleanPreferencesKey("dpi_idle_keepalive_enabled")
        val BLOCK_QUIC_FOR_DPI_BYPASS = booleanPreferencesKey("block_quic_for_dpi_bypass")
        val ANTI_DNS_POISON_ENABLED = booleanPreferencesKey("anti_dns_poison_enabled")
        val DNS_TRANSPORT_MODE = stringPreferencesKey("dns_transport_mode")
    }

    val preferencesFlow: Flow<FirewallPreferencesState> = context.firewallDataStore.data.map { prefs ->
        val themeModeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeModeName } ?: ThemeMode.SYSTEM
        val themeStyleName = prefs[Keys.THEME_STYLE] ?: NetGateThemeStyle.NETGATE_DEFAULT.name
        val themeStyle = NetGateThemeStyle.entries.firstOrNull { it.name == themeStyleName }
            ?: NetGateThemeStyle.NETGATE_DEFAULT
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
        val backgroundAnimationName = prefs[Keys.BACKGROUND_ANIMATION] ?: BackgroundAnimationStyle.NONE.name
        val backgroundAnimation = BackgroundAnimationStyle.entries.firstOrNull { it.name == backgroundAnimationName }
            ?: BackgroundAnimationStyle.NONE
        val appIconName = prefs[Keys.APP_ICON_STYLE] ?: AppIconStyle.EMERALD.name
        val appIconStyle = AppIconStyle.entries.firstOrNull { it.name == appIconName }
            ?: AppIconStyle.EMERALD
        val dpiModeName = prefs[Keys.DPI_BYPASS_MODE] ?: DpiBypassMode.OFF.name
        val dpiMode = DpiBypassMode.entries.firstOrNull { it.name == dpiModeName }
            ?: DpiBypassMode.OFF
        val dnsTransportName = prefs[Keys.DNS_TRANSPORT_MODE] ?: DnsTransportMode.DOH_WITH_FALLBACK.name
        val dnsTransport = DnsTransportMode.entries.firstOrNull { it.name == dnsTransportName }
            ?: DnsTransportMode.DOH_WITH_FALLBACK

        FirewallPreferencesState(
            isFirewallEnabled = prefs[Keys.IS_FIREWALL_ENABLED] ?: false,
            showOfflineApps = prefs[Keys.SHOW_OFFLINE_APPS] ?: false,
            showSystemApps = prefs[Keys.SHOW_SYSTEM_APPS] ?: true,
            defaultBlockWifiForNewRules = prefs[Keys.DEFAULT_BLOCK_WIFI] ?: false,
            defaultBlockDataForNewRules = prefs[Keys.DEFAULT_BLOCK_DATA] ?: false,
            themeMode = themeMode,
            themeStyle = themeStyle,
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
            isBlurAnimationEnabled = prefs[Keys.IS_BLUR_ANIMATION_ENABLED] ?: true,
            backgroundAnimation = backgroundAnimation,
            backgroundParallaxEnabled = prefs[Keys.BACKGROUND_PARALLAX_ENABLED] ?: false,
            backgroundSeasonalEnabled = prefs[Keys.BACKGROUND_SEASONAL_ENABLED] ?: false,
            appIconStyle = appIconStyle,
            hotspotProxyEnabled = prefs[Keys.HOTSPOT_PROXY_ENABLED] ?: false,
            hotspotProxyPort = (prefs[Keys.HOTSPOT_PROXY_PORT] ?: 8890).coerceIn(1024, 65535),
            hotspotApplyNetGateFilters = prefs[Keys.HOTSPOT_APPLY_NETGATE_FILTERS] ?: true,
            hotspotBlockedClientIps = prefs[Keys.HOTSPOT_BLOCKED_CLIENT_IPS] ?: emptySet(),
            hotspotClientCaps = parseClientCaps(prefs[Keys.HOTSPOT_CLIENT_CAPS_SET]),
            hotspotClientLabels = parseClientLabels(prefs[Keys.HOTSPOT_CLIENT_LABELS_SET]),
            globalWifiBlocked = prefs[Keys.GLOBAL_WIFI_BLOCKED] ?: false,
            globalWifiTimerExpiresAtMillis = prefs[Keys.GLOBAL_WIFI_TIMER_EXPIRES_AT] ?: 0L,
            globalWifiTimerDurationMillis = prefs[Keys.GLOBAL_WIFI_TIMER_DURATION_MS] ?: 0L,
            autoBlockWifiOnIdleEnabled = prefs[Keys.AUTO_BLOCK_WIFI_ON_IDLE] ?: false,
            autoBlockWifiIdleMinutes = (prefs[Keys.AUTO_BLOCK_WIFI_IDLE_MINUTES] ?: 10).coerceIn(1, 240),
            autoBlockWifiOpenPanelNotify = prefs[Keys.AUTO_BLOCK_WIFI_PANEL_NOTIFY] ?: true,
            dpiBypassMode = dpiMode,
            dpiFragmentChunkSize = (prefs[Keys.DPI_FRAGMENT_CHUNK_SIZE] ?: 4).coerceIn(1, 32),
            dpiFragmentDelayMs = (prefs[Keys.DPI_FRAGMENT_DELAY_MS] ?: 1).coerceIn(0, 15),
            dpiIdleKeepAliveEnabled = prefs[Keys.DPI_IDLE_KEEPALIVE_ENABLED] ?: true,
            blockQuicForDpiBypass = prefs[Keys.BLOCK_QUIC_FOR_DPI_BYPASS] ?: true,
            antiDnsPoisonEnabled = prefs[Keys.ANTI_DNS_POISON_ENABLED] ?: true,
            dnsTransportMode = dnsTransport
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

    suspend fun setThemeStyle(style: NetGateThemeStyle) {
        context.firewallDataStore.edit { it[Keys.THEME_STYLE] = style.name }
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

    suspend fun setBackgroundAnimation(style: BackgroundAnimationStyle) {
        context.firewallDataStore.edit { it[Keys.BACKGROUND_ANIMATION] = style.name }
    }

    suspend fun setBackgroundParallaxEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.BACKGROUND_PARALLAX_ENABLED] = enabled }
    }

    suspend fun setBackgroundSeasonalEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.BACKGROUND_SEASONAL_ENABLED] = enabled }
    }

    suspend fun setAppIconStyle(style: AppIconStyle) {
        context.firewallDataStore.edit { it[Keys.APP_ICON_STYLE] = style.name }
    }

    suspend fun setHotspotProxyEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.HOTSPOT_PROXY_ENABLED] = enabled }
    }

    suspend fun setHotspotProxyPort(port: Int) {
        context.firewallDataStore.edit { it[Keys.HOTSPOT_PROXY_PORT] = port.coerceIn(1024, 65535) }
    }

    suspend fun setHotspotApplyNetGateFilters(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.HOTSPOT_APPLY_NETGATE_FILTERS] = enabled }
    }

    suspend fun setHotspotClientBlocked(clientIp: String, blocked: Boolean) {
        val clean = clientIp.trim()
        if (clean.isEmpty()) return
        context.firewallDataStore.edit { prefs ->
            val current = prefs[Keys.HOTSPOT_BLOCKED_CLIENT_IPS] ?: emptySet()
            prefs[Keys.HOTSPOT_BLOCKED_CLIENT_IPS] = if (blocked) current + clean else current - clean
        }
    }

    suspend fun setHotspotClientDataCap(clientIp: String, capBytes: Long) {
        val clean = clientIp.trim()
        if (clean.isEmpty()) return
        context.firewallDataStore.edit { prefs ->
            val map = parseClientCaps(prefs[Keys.HOTSPOT_CLIENT_CAPS_SET]).toMutableMap()
            if (capBytes <= 0L) {
                map.remove(clean)
            } else {
                map[clean] = capBytes
            }
            prefs[Keys.HOTSPOT_CLIENT_CAPS_SET] = map.entries.map { "${it.key}|${it.value}" }.toSet()
        }
    }

    suspend fun setHotspotClientLabel(clientIp: String, label: String) {
        val cleanIp = clientIp.trim()
        val cleanLabel = label.trim().replace("|", "")
        if (cleanIp.isEmpty()) return
        context.firewallDataStore.edit { prefs ->
            val map = parseClientLabels(prefs[Keys.HOTSPOT_CLIENT_LABELS_SET]).toMutableMap()
            if (cleanLabel.isEmpty()) {
                map.remove(cleanIp)
            } else {
                map[cleanIp] = cleanLabel
            }
            prefs[Keys.HOTSPOT_CLIENT_LABELS_SET] = map.entries.map { "${it.key}|${it.value}" }.toSet()
        }
    }

    suspend fun setGlobalWifiBlocked(blocked: Boolean) {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_WIFI_BLOCKED] = blocked
            if (!blocked && (prefs[Keys.GLOBAL_WIFI_TIMER_EXPIRES_AT] ?: 0L) <= System.currentTimeMillis()) {
                prefs[Keys.GLOBAL_WIFI_TIMER_EXPIRES_AT] = 0L
                prefs[Keys.GLOBAL_WIFI_TIMER_DURATION_MS] = 0L
            }
        }
    }

    suspend fun setGlobalWifiTimer(expiresAtMillis: Long, durationMillis: Long) {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_WIFI_TIMER_EXPIRES_AT] = expiresAtMillis
            prefs[Keys.GLOBAL_WIFI_TIMER_DURATION_MS] = durationMillis
        }
    }

    suspend fun clearGlobalWifiTimer() {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_WIFI_TIMER_EXPIRES_AT] = 0L
            prefs[Keys.GLOBAL_WIFI_TIMER_DURATION_MS] = 0L
        }
    }

    suspend fun setAutoBlockWifiOnIdle(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.AUTO_BLOCK_WIFI_ON_IDLE] = enabled }
    }

    suspend fun setAutoBlockWifiIdleMinutes(minutes: Int) {
        context.firewallDataStore.edit { it[Keys.AUTO_BLOCK_WIFI_IDLE_MINUTES] = minutes.coerceIn(1, 240) }
    }

    suspend fun setAutoBlockWifiOpenPanelNotify(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.AUTO_BLOCK_WIFI_PANEL_NOTIFY] = enabled }
    }

    suspend fun setDpiBypassMode(mode: DpiBypassMode) {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.DPI_BYPASS_MODE] = mode.name
            if (mode != DpiBypassMode.OFF) {
                prefs[Keys.IS_HTTPS_PROXY_ENABLED] = true
            }
        }
    }

    suspend fun setDpiFragmentChunkSize(size: Int) {
        context.firewallDataStore.edit { it[Keys.DPI_FRAGMENT_CHUNK_SIZE] = size.coerceIn(1, 32) }
    }

    suspend fun setDpiFragmentDelayMs(delayMs: Int) {
        context.firewallDataStore.edit { it[Keys.DPI_FRAGMENT_DELAY_MS] = delayMs.coerceIn(0, 15) }
    }

    suspend fun setDpiIdleKeepAliveEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.DPI_IDLE_KEEPALIVE_ENABLED] = enabled }
    }

    suspend fun setBlockQuicForDpiBypass(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.BLOCK_QUIC_FOR_DPI_BYPASS] = enabled }
    }

    suspend fun setAntiDnsPoisonEnabled(enabled: Boolean) {
        context.firewallDataStore.edit { it[Keys.ANTI_DNS_POISON_ENABLED] = enabled }
    }

    suspend fun setDnsTransportMode(mode: DnsTransportMode) {
        context.firewallDataStore.edit { prefs ->
            prefs[Keys.DNS_TRANSPORT_MODE] = mode.name
            if (mode == DnsTransportMode.DOH_WITH_FALLBACK) {
                prefs[Keys.USE_DOH_UPSTREAM] = true
            }
        }
    }

    private fun parseClientCaps(raw: Set<String>?): Map<String, Long> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Long>()
        for (entry in raw) {
            val parts = entry.split('|', limit = 2)
            if (parts.size == 2) {
                val ip = parts[0].trim()
                val bytes = parts[1].toLongOrNull() ?: 0L
                if (ip.isNotEmpty() && bytes > 0L) out[ip] = bytes
            }
        }
        return out
    }

    private fun parseClientLabels(raw: Set<String>?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (entry in raw) {
            val parts = entry.split('|', limit = 2)
            if (parts.size == 2) {
                val ip = parts[0].trim()
                val label = parts[1].trim()
                if (ip.isNotEmpty() && label.isNotEmpty()) out[ip] = label
            }
        }
        return out
    }
}
