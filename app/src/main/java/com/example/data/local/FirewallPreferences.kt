package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FirewallPreferencesState
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
    }

    val preferencesFlow: Flow<FirewallPreferencesState> = context.firewallDataStore.data.map { prefs ->
        FirewallPreferencesState(
            isFirewallEnabled = prefs[Keys.IS_FIREWALL_ENABLED] ?: false,
            showOfflineApps = prefs[Keys.SHOW_OFFLINE_APPS] ?: false,
            showSystemApps = prefs[Keys.SHOW_SYSTEM_APPS] ?: true,
            defaultBlockWifiForNewRules = prefs[Keys.DEFAULT_BLOCK_WIFI] ?: false,
            defaultBlockDataForNewRules = prefs[Keys.DEFAULT_BLOCK_DATA] ?: false
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
}
