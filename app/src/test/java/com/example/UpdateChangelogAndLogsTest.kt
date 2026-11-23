package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppUpdateStatus
import com.example.data.model.AppUpdateUiState
import com.example.data.model.DnsQueryLogEntry
import com.example.data.model.DnsQueryStatus
import com.example.data.model.DpiBypassMode
import com.example.data.model.FirewallPreferencesState
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.VpnDpiLogCategory
import com.example.data.model.VpnDpiLogEntry
import com.example.data.model.VpnDpiLogSeverity
import com.example.data.model.VpnGuardConnectionState
import com.example.data.model.VpnGuardRoutingMode
import com.example.data.model.VpnGuardRuntimeState
import com.example.data.network.GitHubReleaseUpdateChecker
import com.example.service.VpnRuntimeState
import com.example.ui.VpnDpiLogMonitor
import com.example.ui.components.AppChangelogSheet
import com.example.ui.components.FirewallSettingsSheet
import com.example.ui.components.VpnDpiLogsSheet
import com.example.ui.theme.NetGateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp")
class UpdateChangelogAndLogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `compareVersions handles semantic versions, prefixes, multi-digit parts and prereleases`() {
        assertTrue(GitHubReleaseUpdateChecker.compareVersions("v3.5", "3.4") > 0)
        assertTrue(GitHubReleaseUpdateChecker.compareVersions("v3.4.1", "3.4") > 0)
        assertTrue(GitHubReleaseUpdateChecker.compareVersions("v3.10.0", "3.4") > 0)
        assertEquals(0, GitHubReleaseUpdateChecker.compareVersions("v3.4", "3.4"))
        assertEquals(0, GitHubReleaseUpdateChecker.compareVersions("NetGate-3.4.0", "3.4"))
        assertTrue(GitHubReleaseUpdateChecker.compareVersions("3.3", "3.4") < 0)
        assertTrue(GitHubReleaseUpdateChecker.compareVersions("3.5-beta1", "3.5") < 0)
        assertTrue(GitHubReleaseUpdateChecker.compareVersions("3.5-beta1", "3.4") > 0)
    }

    @Test
    fun `parseReleaseJson extracts tag, apk asset and fallback release metadata`() {
        val json = """
            {
              "tag_name": "v3.5.0",
              "name": "NetGate 3.5",
              "body": "## What's New\n- Improved DPI split\n- Faster DoH fallback",
              "published_at": "2026-06-15T12:00:00Z",
              "html_url": "https://github.com/RanaGzzz/NetGate/releases/tag/v3.5.0",
              "prerelease": false,
              "assets": [
                {
                  "name": "notes.txt",
                  "browser_download_url": "https://example.com/notes.txt",
                  "content_type": "text/plain",
                  "size": 120
                },
                {
                  "name": "NetGate-3.5.0-release.apk",
                  "browser_download_url": "https://github.com/RanaGzzz/NetGate/releases/download/v3.5.0/NetGate-3.5.0-release.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 14500000
                }
              ]
            }
        """.trimIndent()

        val release = GitHubReleaseUpdateChecker.parseReleaseJson(json)
        assertEquals("v3.5.0", release.tagName)
        assertEquals("3.5.0", release.versionName)
        assertEquals("NetGate 3.5", release.title)
        assertEquals("2026-06-15", release.publishedAt)
        assertEquals("NetGate-3.5.0-release.apk", release.apkName)
        assertEquals(14500000L, release.apkSizeBytes)
        assertEquals(
            "https://github.com/RanaGzzz/NetGate/releases/download/v3.5.0/NetGate-3.5.0-release.apk",
            release.preferredOpenUrl
        )

        val noAssetJson = """
            {
              "tag_name": "v3.4.2",
              "name": "Hotfix 3.4.2",
              "body": "- Minor patch",
              "published_at": "2026-06-10T09:00:00Z",
              "html_url": "https://github.com/RanaGzzz/NetGate/releases/tag/v3.4.2",
              "assets": []
            }
        """.trimIndent()
        val fallbackRelease = GitHubReleaseUpdateChecker.parseReleaseJson(noAssetJson)
        assertNull(fallbackRelease.apkDownloadUrl)
        assertEquals(
            "https://github.com/RanaGzzz/NetGate/releases/tag/v3.4.2",
            fallbackRelease.preferredOpenUrl
        )
    }

    @Test
    fun `parseReleasesListJson skips draft releases`() {
        val json = """
            [
              {"tag_name": "v3.6-draft", "draft": true, "html_url": "https://example.com/1"},
              {"tag_name": "v3.5", "draft": false, "html_url": "https://example.com/2"}
            ]
        """.trimIndent()
        val parsed = GitHubReleaseUpdateChecker.parseReleasesListJson(json)
        assertEquals(1, parsed.size)
        assertEquals("v3.5", parsed.first().tagName)
    }

    @Test
    fun `VpnDpiLogMonitor records VPN Guard, Firewall TUN, DPI telemetry deltas and prefs changes`() {
        val monitor = VpnDpiLogMonitor()

        // Baseline initial states
        monitor.onVpnGuardState(VpnGuardRuntimeState())
        monitor.onVpnRuntimeState(VpnRuntimeState())
        monitor.onPreferencesState(FirewallPreferencesState())

        // Preferences DPI switch
        monitor.onPreferencesState(
            FirewallPreferencesState(
                dpiBypassMode = DpiBypassMode.AUTOPILOT,
                dpiFragmentChunkSize = 2
            )
        )

        // VPN Guard connecting -> connected
        monitor.onVpnGuardState(
            VpnGuardRuntimeState(
                connectionState = VpnGuardConnectionState.CONNECTING,
                activeProfileId = "warp-1",
                activeProfileName = "Cloudflare WARP",
                endpoint = "162.159.192.1:2408"
            )
        )
        monitor.onVpnGuardState(
            VpnGuardRuntimeState(
                connectionState = VpnGuardConnectionState.CONNECTED,
                activeProfileId = "warp-1",
                activeProfileName = "Cloudflare WARP",
                endpoint = "162.159.192.1:2408",
                tunnelAddresses = "172.16.0.2/32",
                routingMode = VpnGuardRoutingMode.SELECTED_APPS,
                routedPackagesCount = 3
            )
        )

        // Firewall TUN & DPI counters increment
        monitor.onVpnRuntimeState(
            VpnRuntimeState(
                isServiceRunning = true,
                isTunnelActive = true,
                activeNetworkType = ActiveNetworkType.WIFI,
                dpiBypassMode = DpiBypassMode.AUTOPILOT,
                dpiBypassedConnectionsCount = 5L,
                dpiAutopilotLearnedCount = 2,
                antiDnsPoisonBlockedCount = 1L,
                quicStrippedCount = 4L,
                recentDnsLogs = listOf(
                    DnsQueryLogEntry(
                        id = 99L,
                        domain = "example.com",
                        status = DnsQueryStatus.BLOCKED_HTTPS,
                        reason = "QUIC Downgrade (DPI Bypass)",
                        timestampMs = 1000L
                    )
                )
            )
        )

        val entries = monitor.logs.value
        assertTrue(entries.any { it.category == VpnDpiLogCategory.VPN_GUARD && it.title.contains("Connected") })
        assertTrue(entries.any { it.category == VpnDpiLogCategory.VPN_TUNNEL && it.title.contains("Firewall TUN Active") })
        assertTrue(entries.any { it.category == VpnDpiLogCategory.DPI_BYPASS && it.title.contains("DPI Bypass Reshaped +5") })
        assertTrue(entries.any { it.category == VpnDpiLogCategory.DPI_BYPASS && it.title.contains("Anti-DNS Poisoning Blocked +1") })
        assertTrue(entries.any { it.category == VpnDpiLogCategory.DPI_BYPASS && it.title.contains("QUIC HTTP/3 Downgraded (+4)") })
        assertTrue(entries.any { it.category == VpnDpiLogCategory.DPI_BYPASS && it.title.contains("example.com") })

        val export = monitor.formatForExport()
        assertTrue(export.contains("Cloudflare WARP"))
        assertTrue(export.contains("DPI Bypass"))

        monitor.clear()
        assertTrue(monitor.logs.value.isEmpty())
    }

    @Test
    fun `VpnDpiLogMonitor caps log history at MAX_LOG_ENTRIES`() {
        val monitor = VpnDpiLogMonitor()
        repeat(VpnDpiLogMonitor.MAX_LOG_ENTRIES + 40) { idx ->
            monitor.record(
                category = VpnDpiLogCategory.DPI_BYPASS,
                severity = VpnDpiLogSeverity.INFO,
                title = "Event $idx",
                detail = "Detail $idx"
            )
        }
        assertEquals(VpnDpiLogMonitor.MAX_LOG_ENTRIES, monitor.logs.value.size)
        assertEquals("Event ${VpnDpiLogMonitor.MAX_LOG_ENTRIES + 39}", monitor.logs.value.first().title)
    }

    @Test
    fun `FirewallSettingsSheet triggers GitHub update check, changelog and VPN DPI logs`() {
        var checkedUpdates = false
        var openedChangelog = false
        var openedLogs = false

        val updateState = AppUpdateUiState(
            status = AppUpdateStatus.UPDATE_AVAILABLE,
            currentVersionName = "3.4",
            currentVersionCode = 7,
            latestRelease = GitHubReleaseInfo(
                tagName = "v3.5",
                versionName = "3.5",
                title = "NetGate 3.5",
                body = "- Live VPN & DPI Logs",
                publishedAt = "2026-06-20",
                htmlUrl = "https://github.com/RanaGzzz/NetGate/releases/tag/v3.5",
                apkDownloadUrl = "https://github.com/RanaGzzz/NetGate/releases/download/v3.5/NetGate-3.5.apk",
                apkName = "NetGate-3.5.apk",
                apkSizeBytes = 12_500_000L
            ),
            lastCheckedAtMs = 1_750_000_000_000L
        )

        composeRule.setContent {
            NetGateTheme(dynamicColor = false) {
                FirewallSettingsSheet(
                    preferences = FirewallPreferencesState(),
                    onThemeModeChange = {},
                    onToggleDynamicColor = {},
                    onBlurStyleChange = {},
                    onToggleBlurAnimation = {},
                    onToggleShowSystemApps = {},
                    onToggleShowOfflineApps = {},
                    onToggleStatusBarSpeed = {},
                    onToggleDeepMonitor = {},
                    appUpdateState = updateState,
                    vpnDpiLogsCount = 4,
                    onCheckForUpdates = { checkedUpdates = true },
                    onOpenChangelog = { openedChangelog = true },
                    onOpenVpnDpiLogs = { openedLogs = true },
                    onClearAllRules = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_open_vpn_dpi_logs_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_update_status_badge").performScrollTo().assertTextContains("v3.5", substring = true)
        composeRule.onNodeWithTag("settings_latest_release_summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_download_update_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("settings_check_updates_button").performScrollTo().performClick()
        assertTrue(checkedUpdates)

        composeRule.onNodeWithTag("settings_open_changelog_button").performScrollTo().performClick()
        assertTrue(openedChangelog)

        composeRule.onNodeWithTag("settings_open_vpn_dpi_logs_button").performScrollTo().performClick()
        assertTrue(openedLogs)
    }

    @Test
    fun `AppChangelogSheet displays live GitHub release notes alongside bundled changelog`() {
        val state = AppUpdateUiState(
            status = AppUpdateStatus.UP_TO_DATE,
            currentVersionName = "3.4",
            currentVersionCode = 7,
            recentReleases = listOf(
                GitHubReleaseInfo(
                    tagName = "v3.5",
                    versionName = "3.5",
                    title = "NetGate 3.5 Live",
                    body = "### Highlights\n- Added GitHub Release auto-check",
                    publishedAt = "2026-06-20",
                    htmlUrl = "https://github.com/RanaGzzz/NetGate/releases/tag/v3.5"
                )
            )
        )

        composeRule.setContent {
            NetGateTheme(dynamicColor = false) {
                AppChangelogSheet(
                    updateState = state,
                    onCheckUpdates = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("changelog_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("NetGate 3.5 Live").assertIsDisplayed()
        composeRule.onNodeWithText("Added GitHub Release auto-check", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("changelog_copy_button").assertIsDisplayed()
    }

    @Test
    fun `VpnDpiLogsSheet filters by category and search and clears logs`() {
        var logs by mutableStateOf(
            listOf(
                VpnDpiLogEntry(
                    id = 1L,
                    timestampMs = 1_750_000_001_000L,
                    category = VpnDpiLogCategory.VPN_GUARD,
                    severity = VpnDpiLogSeverity.SUCCESS,
                    title = "VPN Guard Connected (Cloudflare WARP)",
                    detail = "Endpoint 162.159.192.1:2408"
                ),
                VpnDpiLogEntry(
                    id = 2L,
                    timestampMs = 1_750_000_002_000L,
                    category = VpnDpiLogCategory.DPI_BYPASS,
                    severity = VpnDpiLogSeverity.INFO,
                    title = "DPI Bypass Mode: Autopilot",
                    detail = "TLS Record Split -> SNI Split"
                )
            )
        )

        composeRule.setContent {
            NetGateTheme(dynamicColor = false) {
                VpnDpiLogsSheet(
                    logs = logs,
                    onClearLogs = { logs = emptyList() },
                    onCopyLogs = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("vpn_dpi_logs_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("vpn_dpi_log_row_1").assertIsDisplayed()
        composeRule.onNodeWithTag("vpn_dpi_logs_list").performScrollToNode(hasTestTag("vpn_dpi_log_row_2"))
        composeRule.onNodeWithTag("vpn_dpi_log_row_2").assertIsDisplayed()

        // Filter to DPI Bypass only
        composeRule.onNodeWithTag("vpn_dpi_log_filter_dpi_bypass").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_dpi_log_row_2").assertIsDisplayed()

        // Search inside DPI Bypass
        composeRule.onNodeWithTag("vpn_dpi_log_search_input").performTextInput("Autopilot")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_dpi_log_row_2").assertIsDisplayed()

        // Clear logs
        composeRule.onNodeWithTag("vpn_dpi_logs_clear_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_dpi_logs_empty").assertIsDisplayed()
    }
}
