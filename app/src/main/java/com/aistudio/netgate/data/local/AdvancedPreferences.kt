package com.aistudio.netgate.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.netgate.data.model.AdvancedPreferencesState
import com.aistudio.netgate.data.model.HotspotFilterProfile
import com.aistudio.netgate.data.network.OtaUpdateClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.advancedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "netgate_advanced_preferences"
)

/**
 * Settings for the features added on top of the core firewall: kill switch, auto-reconnect,
 * trusted Wi-Fi automation, hotspot client security and OTA updates.
 *
 * Kept in its own DataStore so the large existing preference schema stays untouched.
 */
class AdvancedPreferences(private val context: Context) {

    private object Keys {
        val KILL_SWITCH_ENABLED = booleanPreferencesKey("kill_switch_enabled")
        val KILL_SWITCH_ENGAGED = booleanPreferencesKey("kill_switch_engaged")
        val AUTO_RECONNECT_ENABLED = booleanPreferencesKey("auto_reconnect_enabled")
        val TRUSTED_WIFI_SSIDS = stringSetPreferencesKey("trusted_wifi_ssids")
        val AUTO_VPN_UNKNOWN_WIFI = booleanPreferencesKey("auto_vpn_unknown_wifi")
        val LAST_AUTO_VPN_PROFILE_ID = stringPreferencesKey("last_auto_vpn_profile_id")
        val HOTSPOT_SHARE_ACTIVE_TUNNEL = booleanPreferencesKey("hotspot_share_active_tunnel")
        val HOTSPOT_REQUIRE_APPROVAL = booleanPreferencesKey("hotspot_require_approval")
        val HOTSPOT_APPROVED_CLIENT_IPS = stringSetPreferencesKey("hotspot_approved_client_ips")
        val HOTSPOT_PENDING_CLIENT_IPS = stringSetPreferencesKey("hotspot_pending_client_ips")
        val HOTSPOT_CLIENT_PROFILES = stringSetPreferencesKey("hotspot_client_profiles")
        val HOTSPOT_CLIENT_SPEED_LIMITS = stringSetPreferencesKey("hotspot_client_speed_limits")
        val OTA_MANIFEST_URL = stringPreferencesKey("ota_manifest_url")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
    }

    val flow: Flow<AdvancedPreferencesState> = context.advancedDataStore.data.map { prefs ->
        AdvancedPreferencesState(
            killSwitchEnabled = prefs[Keys.KILL_SWITCH_ENABLED] ?: true,
            killSwitchEngaged = prefs[Keys.KILL_SWITCH_ENGAGED] ?: false,
            autoReconnectEnabled = prefs[Keys.AUTO_RECONNECT_ENABLED] ?: true,
            trustedWifiSsids = prefs[Keys.TRUSTED_WIFI_SSIDS] ?: emptySet(),
            autoVpnOnUnknownWifi = prefs[Keys.AUTO_VPN_UNKNOWN_WIFI] ?: false,
            lastAutoVpnProfileId = prefs[Keys.LAST_AUTO_VPN_PROFILE_ID]?.takeIf { it.isNotBlank() },
            hotspotShareActiveTunnel = prefs[Keys.HOTSPOT_SHARE_ACTIVE_TUNNEL] ?: false,
            hotspotRequireApproval = prefs[Keys.HOTSPOT_REQUIRE_APPROVAL] ?: false,
            hotspotApprovedClientIps = prefs[Keys.HOTSPOT_APPROVED_CLIENT_IPS] ?: emptySet(),
            hotspotPendingClientIps = prefs[Keys.HOTSPOT_PENDING_CLIENT_IPS] ?: emptySet(),
            hotspotClientProfiles = parseProfiles(prefs[Keys.HOTSPOT_CLIENT_PROFILES]),
            hotspotClientSpeedLimits = parseSpeedLimits(prefs[Keys.HOTSPOT_CLIENT_SPEED_LIMITS]),
            otaManifestUrl = prefs[Keys.OTA_MANIFEST_URL] ?: OtaUpdateClient.DEFAULT_MANIFEST_URL,
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true
        )
    }

    suspend fun snapshot(): AdvancedPreferencesState = flow.first()

    suspend fun setKillSwitchEnabled(enabled: Boolean) {
        context.advancedDataStore.edit { it[Keys.KILL_SWITCH_ENABLED] = enabled }
    }

    suspend fun setKillSwitchEngaged(engaged: Boolean) {
        context.advancedDataStore.edit { it[Keys.KILL_SWITCH_ENGAGED] = engaged }
    }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.advancedDataStore.edit { it[Keys.AUTO_RECONNECT_ENABLED] = enabled }
    }

    suspend fun setTrustedWifi(ssid: String, trusted: Boolean) {
        val clean = ssid.trim()
        if (clean.isEmpty()) return
        context.advancedDataStore.edit { prefs ->
            val current = prefs[Keys.TRUSTED_WIFI_SSIDS] ?: emptySet()
            prefs[Keys.TRUSTED_WIFI_SSIDS] = if (trusted) current + clean else current - clean
        }
    }

    suspend fun setAutoVpnOnUnknownWifi(enabled: Boolean) {
        context.advancedDataStore.edit { it[Keys.AUTO_VPN_UNKNOWN_WIFI] = enabled }
    }

    suspend fun setLastAutoVpnProfileId(profileId: String?) {
        context.advancedDataStore.edit { prefs ->
            if (profileId.isNullOrBlank()) {
                prefs.remove(Keys.LAST_AUTO_VPN_PROFILE_ID)
            } else {
                prefs[Keys.LAST_AUTO_VPN_PROFILE_ID] = profileId
            }
        }
    }

    suspend fun setHotspotShareActiveTunnel(enabled: Boolean) {
        context.advancedDataStore.edit { it[Keys.HOTSPOT_SHARE_ACTIVE_TUNNEL] = enabled }
    }

    suspend fun setHotspotRequireApproval(enabled: Boolean) {
        context.advancedDataStore.edit { it[Keys.HOTSPOT_REQUIRE_APPROVAL] = enabled }
    }

    suspend fun approveHotspotClient(clientIp: String) {
        val clean = clientIp.trim()
        if (clean.isEmpty()) return
        context.advancedDataStore.edit { prefs ->
            prefs[Keys.HOTSPOT_APPROVED_CLIENT_IPS] = (prefs[Keys.HOTSPOT_APPROVED_CLIENT_IPS] ?: emptySet()) + clean
            prefs[Keys.HOTSPOT_PENDING_CLIENT_IPS] = (prefs[Keys.HOTSPOT_PENDING_CLIENT_IPS] ?: emptySet()) - clean
        }
    }

    suspend fun revokeHotspotClient(clientIp: String) {
        val clean = clientIp.trim()
        if (clean.isEmpty()) return
        context.advancedDataStore.edit { prefs ->
            prefs[Keys.HOTSPOT_APPROVED_CLIENT_IPS] = (prefs[Keys.HOTSPOT_APPROVED_CLIENT_IPS] ?: emptySet()) - clean
        }
    }

    suspend fun markHotspotClientPending(clientIp: String) {
        val clean = clientIp.trim()
        if (clean.isEmpty()) return
        context.advancedDataStore.edit { prefs ->
            prefs[Keys.HOTSPOT_PENDING_CLIENT_IPS] = (prefs[Keys.HOTSPOT_PENDING_CLIENT_IPS] ?: emptySet()) + clean
        }
    }

    suspend fun setHotspotClientProfile(clientIp: String, profile: HotspotFilterProfile) {
        val clean = clientIp.trim()
        if (clean.isEmpty()) return
        context.advancedDataStore.edit { prefs ->
            val map = parseProfiles(prefs[Keys.HOTSPOT_CLIENT_PROFILES]).toMutableMap()
            map[clean] = profile
            prefs[Keys.HOTSPOT_CLIENT_PROFILES] = map.entries.map { "${it.key}|${it.value.name}" }.toSet()
        }
    }

    suspend fun setHotspotClientSpeedLimit(clientIp: String, bytesPerSec: Long) {
        val clean = clientIp.trim()
        if (clean.isEmpty()) return
        context.advancedDataStore.edit { prefs ->
            val map = parseSpeedLimits(prefs[Keys.HOTSPOT_CLIENT_SPEED_LIMITS]).toMutableMap()
            if (bytesPerSec <= 0L) map.remove(clean) else map[clean] = bytesPerSec
            prefs[Keys.HOTSPOT_CLIENT_SPEED_LIMITS] = map.entries.map { "${it.key}|${it.value}" }.toSet()
        }
    }

    suspend fun setOtaManifestUrl(url: String) {
        context.advancedDataStore.edit {
            it[Keys.OTA_MANIFEST_URL] = url.trim().ifEmpty { OtaUpdateClient.DEFAULT_MANIFEST_URL }
        }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.advancedDataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    /** Whole-store snapshot for encrypted backups. */
    suspend fun exportRawSnapshot(): Map<String, String> =
        DataStoreBackupCodec.encode(context.advancedDataStore.data.first())

    suspend fun importRawSnapshot(values: Map<String, String>) {
        context.advancedDataStore.edit { prefs -> DataStoreBackupCodec.apply(prefs, values) }
    }

    private fun parseProfiles(raw: Set<String>?): Map<String, HotspotFilterProfile> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, HotspotFilterProfile>()
        for (entry in raw) {
            val parts = entry.split('|', limit = 2)
            if (parts.size == 2) {
                val ip = parts[0].trim()
                if (ip.isNotEmpty()) out[ip] = HotspotFilterProfile.fromRaw(parts[1])
            }
        }
        return out
    }

    private fun parseSpeedLimits(raw: Set<String>?): Map<String, Long> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Long>()
        for (entry in raw) {
            val parts = entry.split('|', limit = 2)
            if (parts.size == 2) {
                val ip = parts[0].trim()
                val limit = parts[1].toLongOrNull() ?: 0L
                if (ip.isNotEmpty() && limit > 0L) out[ip] = limit
            }
        }
        return out
    }
}
