package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.ThemeMode
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
    }

    val preferencesFlow: Flow<FirewallPreferencesState> = context.firewallDataStore.data.map { prefs ->
        val themeModeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeModeName } ?: ThemeMode.SYSTEM

        FirewallPreferencesState(
            isFirewallEnabled = prefs[Keys.IS_FIREWALL_ENABLED] ?: false,
            showOfflineApps = prefs[Keys.SHOW_OFFLINE_APPS] ?: false,
            showSystemApps = prefs[Keys.SHOW_SYSTEM_APPS] ?: true,
            defaultBlockWifiForNewRules = prefs[Keys.DEFAULT_BLOCK_WIFI] ?: false,
            defaultBlockDataForNewRules = prefs[Keys.DEFAULT_BLOCK_DATA] ?: false,
            themeMode = themeMode,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true
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
}
