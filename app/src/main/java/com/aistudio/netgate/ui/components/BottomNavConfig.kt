package com.aistudio.netgate.ui.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.netgate.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BottomNavTabId(
    val title: String,
    val testTag: String
) {
    FIREWALL("Firewall", "nav_tab_firewall"),
    AD_BLOCKER("Ad Blocker", "nav_tab_websites"),
    SNI_DPI("SNI & DPI", "nav_tab_sni_dpi"),
    VPN("VPN", "floating_nav_vpn_guard"),
    DNS("DNS", "floating_nav_dns"),
    HOTSPOT("Hotspot", "floating_nav_hotspot"),
    USAGE("Usage", "nav_tab_data_usage"),
    WIFI("Wi-Fi", "nav_tab_wifi"),
    SPEED_TEST("Speed Test", "nav_tab_speed_test");

    @Composable
    fun resolveIcon(): ImageVector {
        return when (this) {
            FIREWALL -> Icons.Rounded.Shield
            AD_BLOCKER -> Icons.Rounded.Public
            SNI_DPI -> Icons.Rounded.Settings
            VPN -> ImageVector.vectorResource(id = R.drawable.ic_vpn_tunnel_shield)
            DNS -> Icons.Rounded.Dns
            HOTSPOT -> Icons.Rounded.WifiTethering
            USAGE -> Icons.Rounded.BarChart
            WIFI -> Icons.Rounded.Wifi
            SPEED_TEST -> Icons.Rounded.Speed
        }
    }
}

enum class BottomNavHeightOption(val label: String, val heightDp: Dp) {
    COMPACT("Compact (48dp)", 48.dp),
    STANDARD("Standard (54dp)", 54.dp),
    SPACIOUS("Spacious (62dp)", 62.dp)
}

enum class BottomNavWidthOption(val label: String, val maxDp: Dp) {
    COMPACT("Compact (480dp)", 480.dp),
    STANDARD("Standard (580dp)", 580.dp),
    EXPANDED("Full (680dp)", 680.dp)
}

enum class BottomNavIconScale(
    val label: String,
    val iconSize: Dp,
    val fontSize: TextUnit,
    val minTabWidth: Dp
) {
    SMALL("Small", 18.dp, 8.5.sp, 44.dp),
    STANDARD("Standard", 20.dp, 9.5.sp, 48.dp),
    LARGE("Large", 22.dp, 10.5.sp, 54.dp)
}

/** Default number of tabs fully visible at once; remaining tabs are reached by swiping the bar. */
const val DEFAULT_NAV_VISIBLE_COUNT = 5

/** How many times the one-time "swipe me" nudge animation may play before it stops for good. */
const val MAX_NAV_SWIPE_NUDGES = 3

data class BottomNavConfig(
    val heightOption: BottomNavHeightOption = BottomNavHeightOption.STANDARD,
    val widthOption: BottomNavWidthOption = BottomNavWidthOption.STANDARD,
    val iconScale: BottomNavIconScale = BottomNavIconScale.STANDARD,
    val orderedTabIds: List<BottomNavTabId> = BottomNavTabId.entries,
    val visibleTabIds: Set<BottomNavTabId> = BottomNavTabId.entries.toSet(),
    val visibleCount: Int = DEFAULT_NAV_VISIBLE_COUNT
) {
    val activeOrderedTabs: List<BottomNavTabId>
        get() = orderedTabIds.filter { it in visibleTabIds }
}

object BottomNavConfigManager {
    private const val PREFS_NAME = "netgate_bottom_nav_customization"
    private const val KEY_HEIGHT = "key_nav_height"
    private const val KEY_WIDTH = "key_nav_width"
    private const val KEY_SCALE = "key_nav_scale"
    private const val KEY_ORDER = "key_nav_order"
    private const val KEY_VISIBLE = "key_nav_visible_v2"
    private const val KEY_VISIBLE_COUNT = "key_nav_visible_count"
    private const val KEY_NUDGE_COUNT = "key_nav_nudge_count"

    private val _configFlow = MutableStateFlow(BottomNavConfig())
    val configFlow: StateFlow<BottomNavConfig> = _configFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val height = runCatching {
            BottomNavHeightOption.valueOf(prefs.getString(KEY_HEIGHT, BottomNavHeightOption.STANDARD.name)!!)
        }.getOrDefault(BottomNavHeightOption.STANDARD)

        val width = runCatching {
            BottomNavWidthOption.valueOf(prefs.getString(KEY_WIDTH, BottomNavWidthOption.STANDARD.name)!!)
        }.getOrDefault(BottomNavWidthOption.STANDARD)

        val scale = runCatching {
            BottomNavIconScale.valueOf(prefs.getString(KEY_SCALE, BottomNavIconScale.STANDARD.name)!!)
        }.getOrDefault(BottomNavIconScale.STANDARD)

        val orderRaw = prefs.getString(KEY_ORDER, null)
        val orderedList = if (!orderRaw.isNullOrBlank()) {
            orderRaw.split(",").mapNotNull { name ->
                runCatching { BottomNavTabId.valueOf(name) }.getOrNull()
            }.distinct().let { loaded ->
                val missing = BottomNavTabId.entries.filter { it !in loaded }
                loaded + missing
            }
        } else {
            BottomNavTabId.entries
        }

        val visibleRaw = prefs.getString(KEY_VISIBLE, null)
        val visibleSet = if (!visibleRaw.isNullOrBlank()) {
            visibleRaw.split(",").mapNotNull { name ->
                runCatching { BottomNavTabId.valueOf(name) }.getOrNull()
            }.toSet().ifEmpty { BottomNavTabId.entries.toSet() }
        } else {
            BottomNavTabId.entries.toSet()
        }

        val visibleCount = prefs.getInt(KEY_VISIBLE_COUNT, DEFAULT_NAV_VISIBLE_COUNT)
            .coerceIn(1, BottomNavTabId.entries.size)

        _configFlow.value = BottomNavConfig(
            heightOption = height,
            widthOption = width,
            iconScale = scale,
            orderedTabIds = orderedList,
            visibleTabIds = visibleSet,
            visibleCount = visibleCount
        )
    }

    /** Sets how many tabs are fully visible at once (1..all). The rest are reached by swiping. */
    fun setVisibleCount(context: Context, count: Int) {
        val clamped = count.coerceIn(1, BottomNavTabId.entries.size)
        if (clamped == _configFlow.value.visibleCount) return
        val updated = _configFlow.value.copy(visibleCount = clamped)
        _configFlow.value = updated
        persist(context, updated)
    }

    fun getNudgeCount(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_NUDGE_COUNT, 0)

    fun incrementNudge(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(
            KEY_NUDGE_COUNT,
            (prefs.getInt(KEY_NUDGE_COUNT, 0) + 1).coerceAtMost(MAX_NAV_SWIPE_NUDGES)
        ).apply()
    }

    /** Called once the user has swiped the bar themselves, so the hint never plays again. */
    fun markNudgeDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_NUDGE_COUNT, MAX_NAV_SWIPE_NUDGES).apply()
    }

    fun setHeightOption(context: Context, option: BottomNavHeightOption) {
        val updated = _configFlow.value.copy(heightOption = option)
        _configFlow.value = updated
        persist(context, updated)
    }

    fun setWidthOption(context: Context, option: BottomNavWidthOption) {
        val updated = _configFlow.value.copy(widthOption = option)
        _configFlow.value = updated
        persist(context, updated)
    }

    fun setIconScale(context: Context, scale: BottomNavIconScale) {
        val updated = _configFlow.value.copy(iconScale = scale)
        _configFlow.value = updated
        persist(context, updated)
    }

    fun toggleTabVisibility(context: Context, tabId: BottomNavTabId): Boolean {
        val currentVisible = _configFlow.value.visibleTabIds
        return if (tabId in currentVisible) {
            if (currentVisible.size <= 1) {
                false
            } else {
                val updated = _configFlow.value.copy(visibleTabIds = currentVisible - tabId)
                _configFlow.value = updated
                persist(context, updated)
                true
            }
        } else {
            val updated = _configFlow.value.copy(visibleTabIds = currentVisible + tabId)
            _configFlow.value = updated
            persist(context, updated)
            true
        }
    }

    fun moveTabUp(context: Context, tabId: BottomNavTabId) {
        val current = _configFlow.value.orderedTabIds.toMutableList()
        val index = current.indexOf(tabId)
        if (index > 0) {
            current[index] = current[index - 1]
            current[index - 1] = tabId
            val updated = _configFlow.value.copy(orderedTabIds = current)
            _configFlow.value = updated
            persist(context, updated)
        }
    }

    fun moveTabDown(context: Context, tabId: BottomNavTabId) {
        val current = _configFlow.value.orderedTabIds.toMutableList()
        val index = current.indexOf(tabId)
        if (index in 0 until current.lastIndex) {
            current[index] = current[index + 1]
            current[index + 1] = tabId
            val updated = _configFlow.value.copy(orderedTabIds = current)
            _configFlow.value = updated
            persist(context, updated)
        }
    }

    fun resetToDefaults(context: Context) {
        val default = BottomNavConfig()
        _configFlow.value = default
        persist(context, default)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_NUDGE_COUNT, 0).apply()
    }

    private fun persist(context: Context, config: BottomNavConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_HEIGHT, config.heightOption.name)
            .putString(KEY_WIDTH, config.widthOption.name)
            .putString(KEY_SCALE, config.iconScale.name)
            .putString(KEY_ORDER, config.orderedTabIds.joinToString(",") { it.name })
            .putString(KEY_VISIBLE, config.visibleTabIds.joinToString(",") { it.name })
            .putInt(KEY_VISIBLE_COUNT, config.visibleCount)
            .apply()
    }
}

val LocalBottomNavConfig = compositionLocalOf { BottomNavConfig() }
