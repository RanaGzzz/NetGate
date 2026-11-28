package com.aistudio.netgate

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.ActiveNetworkType
import com.aistudio.netgate.data.model.AdFilterListItem
import com.aistudio.netgate.data.model.AppCountdownTimer
import com.aistudio.netgate.data.model.AppDestination
import com.aistudio.netgate.data.model.AppFilterMode
import com.aistudio.netgate.data.model.AppUsageBreakdownItem
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.ConnectionDetails
import com.aistudio.netgate.data.model.ConnectionRiskLevel
import com.aistudio.netgate.data.model.CountryTrafficSummary
import com.aistudio.netgate.data.model.DailyTrafficHistoryPoint
import com.aistudio.netgate.data.model.DataUsageSummary
import com.aistudio.netgate.data.model.DataUsageSubTab
import com.aistudio.netgate.data.model.DnsCheckState
import com.aistudio.netgate.data.model.DnsLeakCheckItem
import com.aistudio.netgate.data.model.DnsLeakCheckStatus
import com.aistudio.netgate.data.model.DnsLeakTestState
import com.aistudio.netgate.data.model.DnsLeakVerdict
import com.aistudio.netgate.data.model.DnsProbeState
import com.aistudio.netgate.data.model.DnsQueryLogEntry
import com.aistudio.netgate.data.model.DnsQueryStatus
import com.aistudio.netgate.data.model.DnsServerIdentity
import com.aistudio.netgate.data.model.FirewallPreferencesState
import com.aistudio.netgate.data.model.HistoricalUsageTotals
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.SiteConnectionRecord
import com.aistudio.netgate.data.model.SniProxyPreset
import com.aistudio.netgate.data.model.StatusBarSpeedStyle
import com.aistudio.netgate.data.model.ThemeMode
import com.aistudio.netgate.data.model.ThroughputSample
import com.aistudio.netgate.data.model.UpstreamDnsServer
import com.aistudio.netgate.data.model.WebsiteFilterMode
import com.aistudio.netgate.data.model.WebsiteRuleItem
import com.aistudio.netgate.data.model.YouTubeRestrictedMode
import com.aistudio.netgate.service.VpnRuntimeState
import com.aistudio.netgate.ui.DataUsageContent
import com.aistudio.netgate.ui.DataUsageUiState
import com.aistudio.netgate.ui.WebsiteBlockerContent
import com.aistudio.netgate.ui.WebsiteBlockerUiState
import com.aistudio.netgate.ui.components.AppRuleRow
import com.aistudio.netgate.ui.components.DnsProtectionContent
import com.aistudio.netgate.ui.components.FirewallHeaderCard
import com.aistudio.netgate.ui.components.FloatingFrostedNavBar
import com.aistudio.netgate.ui.components.FrostedBackdrop
import com.aistudio.netgate.ui.components.FrostedSurface
import com.aistudio.netgate.ui.components.GlassTabItem
import com.aistudio.netgate.ui.components.GlassTabVariant
import com.aistudio.netgate.ui.components.GlassTabs
import com.aistudio.netgate.ui.theme.NetGateTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class ScreenshotGeneratorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val nowMs = 1_760_000_000_000L

    private val sampleConnection = ConnectionDetails(
        activeNetworkType = ActiveNetworkType.WIFI,
        isWifiRadioEnabled = true,
        isWifiConnected = true,
        isMobileDataEnabled = true,
        isMobileDataConnected = false,
        interfaceName = "wlan0",
        ipv4Address = "192.168.1.42",
        gatewayAddress = "192.168.1.1",
        dnsServers = listOf("10.1.10.2"),
        isValidated = true,
        downstreamKbps = 185_000,
        upstreamKbps = 42_000,
        wifiRssiDbm = -49,
        wifiSignalLabel = "Excellent",
        wifiLinkSpeedMbps = 866,
        wifiRxLinkSpeedMbps = 866,
        wifiFrequencyMhz = 5180,
        wifiBandLabel = "5 GHz",
        wifiStandardLabel = "Wi-Fi 6"
    )

    private val sampleTelemetry = LiveNetworkTelemetry(
        downloadBytesPerSec = 2_840_000L,
        uploadBytesPerSec = 640_000L,
        peakDownloadBytesPerSec = 6_420_000L,
        peakUploadBytesPerSec = 1_120_000L,
        sessionRxBytes = 418_500_000L,
        sessionTxBytes = 64_200_000L,
        todayWifiRxBytes = 1_480_000_000L,
        todayWifiTxBytes = 245_000_000L,
        todayMobileRxBytes = 210_000_000L,
        todayMobileTxBytes = 38_000_000L,
        activeNetworkType = ActiveNetworkType.WIFI,
        activeInterfaceName = "wlan0",
        historySamples = List(16) { idx ->
            ThroughputSample(
                timestampMs = nowMs - (15 - idx) * 1000L,
                rxBytesPerSec = (900_000L + ((idx * 370_000L) % 2_400_000L)),
                txBytesPerSec = (180_000L + ((idx * 95_000L) % 520_000L))
            )
        },
        isMonitorServiceRunning = true,
        isTrafficStatsSupported = true
    )

    private val sampleApps = listOf(
        ManagedApp(
            packageName = "com.android.chrome",
            appName = "Chrome Browser",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = false,
            blockMobileData = false
        ),
        ManagedApp(
            packageName = "com.social.feed",
            appName = "Social Reels",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = true,
            blockMobileData = true,
            activeTimer = AppCountdownTimer(
                packageName = "com.social.feed",
                blockWifiOnExpire = true,
                blockMobileDataOnExpire = true,
                durationMillis = 15 * 60_000L,
                expiresAtMillis = nowMs + 11 * 60_000L + 24_000L,
                createdAtMillis = nowMs - 3 * 60_000L
            )
        ),
        ManagedApp(
            packageName = "com.video.player",
            appName = "StreamBox HD",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = false,
            blockMobileData = true
        ),
        ManagedApp(
            packageName = "com.gaming.arena",
            appName = "Arcade Legends",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = true,
            blockMobileData = true
        ),
        ManagedApp(
            packageName = "com.bank.wallet",
            appName = "Secure Pay",
            isSystemApp = false,
            hasInternetPermission = true,
            blockWifi = false,
            blockMobileData = false
        )
    )

    @Test
    fun `capture firewall screenshot`() {
        val outDir = resolveScreenshotsDir().apply { mkdirs() }
        captureScreen(File(outDir, "firewall.png")) {
            ScreenshotShell(
                destination = AppDestination.FIREWALL,
                title = "NetGate",
                subtitle = "Wi-Fi & Data Controller"
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        FirewallHeaderCard(
                            isFirewallActive = true,
                            connectionDetails = sampleConnection,
                            totalAppsCount = 48,
                            wifiBlockedCount = 6,
                            dataBlockedCount = 11,
                            activeBlockedNowCount = 6,
                            isAdBlockerEnabled = true,
                            blockedAdsCount = 342L,
                            liveTelemetry = sampleTelemetry,
                            onToggleFirewall = {},
                            onOpenConnectionInfo = {},
                            onOpenDnsProtection = {},
                            onOpenSpeedTest = {},
                            onOpenAdBlocker = {},
                            onOpenNetworkMonitor = {}
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FrostedSurface(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.search_apps_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val previewModes = listOf(
                            AppFilterMode.ALL to 48,
                            AppFilterMode.BLOCKED to 12,
                            AppFilterMode.WIFI_BLOCKED to 6
                        )
                        GlassTabs(
                            items = previewModes.map { (mode, count) ->
                                GlassTabItem(label = "${mode.label} ($count)")
                            },
                            selectedIndex = 0,
                            onSelect = {},
                            variant = GlassTabVariant.CHIP,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(sampleApps, key = { it.packageName }) { app ->
                        AppRuleRow(
                            app = app,
                            activeNetworkType = ActiveNetworkType.WIFI,
                            isFirewallActive = true,
                            isSelectionMode = false,
                            isSelected = false,
                            nowMillis = nowMs,
                            loadIcon = { null },
                            onToggleWifi = {},
                            onToggleData = {},
                            onRowClick = {},
                            onRowLongClick = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `capture websites screenshot`() {
        val outDir = resolveScreenshotsDir().apply { mkdirs() }
        captureScreen(File(outDir, "websites.png")) {
            val websiteState = WebsiteBlockerUiState(
                filterMode = WebsiteFilterMode.BLOCKLIST,
                isAdBlockerEnabled = true,
                isEssentialPassthroughEnabled = true,
                useDohUpstream = true,
                selectedUpstreamDns = UpstreamDnsServer.ADGUARD_DNS,
                isHttpsProxyEnabled = true,
                isHttpsProxyActive = true,
                httpsProxyPort = 8899,
                isProxyStripTrackingParams = true,
                blockedProxyRequestsCount = 128L,
                allowedProxyRequestsCount = 1940L,
                adFilterLists = listOf(
                    AdFilterListItem(
                        id = "stevenblack",
                        title = "StevenBlack Unified Hosts",
                        description = "Adware & malware unified hosts",
                        sourceUrl = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                        category = "Ads + Malware",
                        isEnabled = true,
                        domainCount = 148200,
                        lastSyncedAt = nowMs - 120_000L
                    ),
                    AdFilterListItem(
                        id = "adguard_dns",
                        title = "AdGuard DNS Filter",
                        description = "Mobile ads, trackers & telemetry",
                        sourceUrl = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
                        category = "Ads + Trackers",
                        isEnabled = true,
                        domainCount = 62400,
                        lastSyncedAt = nowMs - 120_000L
                    )
                ),
                loadedAdDomainsCount = 210600,
                blockedAdsCount = 342L,
                lifetimeAdsBlockedCount = 14820L,
                dnsQueryLogs = listOf(
                    DnsQueryLogEntry(1L, "ads.youtube.com", DnsQueryStatus.BLOCKED_AD, "YouTube Ad Block", nowMs - 3000L),
                    DnsQueryLogEntry(2L, "doubleclick.net", DnsQueryStatus.BLOCKED_AD, "AdGuard DNS Filter", nowMs - 9000L),
                    DnsQueryLogEntry(3L, "www.google.com", DnsQueryStatus.SAFE_REWRITE, "SafeSearch enforced", nowMs - 15000L)
                ),
                blocklistRules = listOf(
                    WebsiteRuleItem("tiktok.com", isWhitelist = false, isEnabled = true, createdAt = nowMs - 500_000L)
                ),
                whitelistRules = listOf(
                    WebsiteRuleItem("khanacademy.org", isWhitelist = true, isEnabled = true, createdAt = nowMs - 400_000L)
                ),
                blockedDnsCount = 389L,
                allowedDnsCount = 2410L,
                isSafeSearchEnabled = true,
                youtubeRestrictedMode = YouTubeRestrictedMode.STRICT,
                isYoutubeAdBlockEnabled = true,
                sniProxyPreset = SniProxyPreset.FAMILY_SAFE,
                safeRewriteCount = 47L
            )
            ScreenshotShell(
                destination = AppDestination.WEBSITES,
                title = "Ad & Network Guard",
                subtitle = "System Ad Filter, SNI & DPI Lockdown"
            ) {
                WebsiteBlockerContent(
                    state = websiteState,
                    allManagedApps = sampleApps,
                    isFirewallActive = true,
                    connectionDetails = sampleConnection,
                    loadIcon = { null },
                    onSelectFilterMode = {},
                    onAddWebsiteRule = { _, _ -> },
                    onToggleWebsiteRuleEnabled = { _, _ -> },
                    onDeleteWebsiteRule = {},
                    onClearWebsiteRules = {},
                    onToggleBypassApp = {},
                    onClearBypassApps = {},
                    onToggleAdBlocker = {},
                    onToggleAdFilterList = { _, _ -> },
                    onSyncAdFilterLists = {},
                    onToggleEssentialPassthrough = {},
                    onToggleUseDohUpstream = {},
                    onToggleHttpsProxy = {},
                    onSelectProxyPort = {},
                    onToggleProxyStripTracking = {},
                    onToggleSafeSearch = {},
                    onSelectYoutubeMode = {},
                    onToggleYoutubeAdBlock = {},
                    onSelectSniPreset = {},
                    onSaveCustomSniProxy = { _, _ -> },
                    onSelectDpiMode = {},
                    onSelectDpiChunkSize = {},
                    onToggleDpiIdleKeepAlive = {},
                    onToggleBlockQuic = {},
                    onToggleAntiDnsPoison = {},
                    onSelectDnsTransportMode = {},
                    onFlushDpiCache = {},
                    onAddCustomAdFilterList = { _, _, _ -> },
                    onDeleteCustomAdFilterList = {},
                    onCopiedProxyEndpoint = {},
                    onClearDnsLogs = {},
                    onRequestEnableFirewall = {},
                    onOpenDnsProtection = {}
                )
            }
        }
    }

    @Test
    fun `capture usage screenshot`() {
        val outDir = resolveScreenshotsDir().apply { mkdirs() }
        captureScreen(File(outDir, "usage.png")) {
            val usageState = DataUsageUiState(
                selectedSubTab = DataUsageSubTab.OVERVIEW,
                hasUsageAccess = true,
                summary = DataUsageSummary(
                    blockedTotalBytes = 420_000_000L,
                    blockedWifiBytes = 310_000_000L,
                    blockedMobileBytes = 110_000_000L,
                    allowedTotalBytes = 1_650_000_000L,
                    allowedWifiBytes = 1_390_000_000L,
                    allowedMobileBytes = 260_000_000L,
                    totalWifiRxBytes = 1_480_000_000L,
                    totalWifiTxBytes = 220_000_000L,
                    totalMobileRxBytes = 315_000_000L,
                    totalMobileTxBytes = 55_000_000L,
                    blockedAppsCount = 12,
                    allowedAppsCount = 36
                ),
                appItems = listOf(
                    AppUsageBreakdownItem(
                        packageName = "com.video.player",
                        appName = "StreamBox HD",
                        isSystemApp = false,
                        wifiBytes = 840_000_000L,
                        mobileBytes = 120_000_000L,
                        wifiRxBytes = 810_000_000L,
                        wifiTxBytes = 30_000_000L,
                        mobileRxBytes = 112_000_000L,
                        mobileTxBytes = 8_000_000L,
                        blockWifi = false,
                        blockMobileData = true
                    ),
                    AppUsageBreakdownItem(
                        packageName = "com.android.chrome",
                        appName = "Chrome Browser",
                        isSystemApp = false,
                        wifiBytes = 510_000_000L,
                        mobileBytes = 95_000_000L,
                        wifiRxBytes = 465_000_000L,
                        wifiTxBytes = 45_000_000L,
                        mobileRxBytes = 85_000_000L,
                        mobileTxBytes = 10_000_000L,
                        blockWifi = false,
                        blockMobileData = false
                    )
                ),
                dailyHistory = List(7) { d ->
                    DailyTrafficHistoryPoint(
                        dateEpochDay = 20400L + d,
                        wifiRxBytes = 380_000_000L + d * 45_000_000L,
                        wifiTxBytes = 55_000_000L + d * 7_000_000L,
                        mobileRxBytes = 90_000_000L + d * 12_000_000L,
                        mobileTxBytes = 18_000_000L + d * 2_000_000L,
                        appsCount = 14 + d
                    )
                },
                historicalTotals = HistoricalUsageTotals(
                    totalWifiRxBytes = 3_420_000_000L,
                    totalWifiTxBytes = 490_000_000L,
                    totalMobileRxBytes = 790_000_000L,
                    totalMobileTxBytes = 135_000_000L,
                    daysTrackedCount = 7
                ),
                liveConnections = listOf(
                    SiteConnectionRecord(
                        id = 1L,
                        domain = "api.github.com",
                        remoteIp = "140.82.121.6",
                        remotePort = 443,
                        protocol = "HTTPS",
                        appPackage = "com.android.chrome",
                        appName = "Chrome Browser",
                        countryCode = "US",
                        countryName = "United States",
                        regionName = "Virginia",
                        ispOrOrg = "GitHub, Inc.",
                        bytesSent = 240_000L,
                        bytesReceived = 1_850_000L,
                        actionStatus = "ALLOWED",
                        riskLevel = ConnectionRiskLevel.SAFE,
                        riskReason = "Verified TLS endpoint",
                        hitCount = 14,
                        firstSeenAt = nowMs - 60_000L,
                        lastSeenAt = nowMs - 1_500L
                    )
                ),
                countrySummaries = listOf(
                    CountryTrafficSummary("US", "United States", "Virginia", 240_000L, 1_850_000L, 4, 14, 0)
                )
            )
            ScreenshotShell(
                destination = AppDestination.DATA_USAGE,
                title = "Network & Usage",
                subtitle = "Live Monitor, Sites, Regions & Audit"
            ) {
                DataUsageContent(
                    state = usageState,
                    liveTelemetry = sampleTelemetry,
                    isFirewallActive = true,
                    isDeepMonitorEnabled = true,
                    isHttpsProxyActive = true,
                    isStatusBarSpeedEnabled = true,
                    statusBarSpeedStyle = StatusBarSpeedStyle.DOWNLOAD_UPLOAD,
                    showStatusBarDailyUsage = true,
                    loadIcon = { null },
                    onSelectSubTab = {},
                    onTimeRangeChange = {},
                    onChartModeChange = {},
                    onFilterCategoryChange = {},
                    onRefreshUsage = {},
                    onClearHistory = {},
                    onInspectApp = {},
                    onToggleStatusBarSpeed = {},
                    onSelectStatusBarStyle = {},
                    onToggleShowDailyUsage = {},
                    onResetLiveSession = {},
                    onRequestEnableFirewall = {},
                    onToggleDeepMonitor = {},
                    onSetShowLiveSitesFeed = {},
                    onSiteFilterChange = {},
                    onSiteSearchQueryChange = {},
                    onToggleCountryFilter = {},
                    onClearConnectionHistory = {},
                    onQuickBlockSite = {},
                    onQuickAllowSite = {},
                    onRunSecurityAudit = {},
                    onOpenFlaggedSitesFromAudit = {},
                    speedTestState = com.aistudio.netgate.data.network.SpeedTestState(),
                    activeNetworkLabel = "Wi-Fi",
                    onStartSpeedTest = {},
                    onStopSpeedTest = {},
                    onRunVideoTest = {}
                )
            }
        }
    }

    @Test
    fun `capture dns screenshot`() {
        val outDir = resolveScreenshotsDir().apply { mkdirs() }
        captureScreen(File(outDir, "dns.png")) {
            val prefs = FirewallPreferencesState(
                isFirewallEnabled = true,
                useDohUpstream = true,
                selectedUpstreamDns = UpstreamDnsServer.CLOUDFLARE_STANDARD,
                isHttpsProxyEnabled = true
            )
            val dnsCheck = DnsCheckState(
                isChecking = false,
                connectedProvider = "Cloudflare 1.1.1.1",
                transportLabel = "Encrypted DoH via NetGate VPN",
                isEncrypted = true,
                resolvedExitDnsIp = "172.64.36.1",
                resolvedExitDnsOrg = "Cloudflare, Inc.",
                resolvedExitDnsLocation = "San Jose, United States",
                resolvedExitDnsFlag = "\uD83C\uDDFA\uD83C\uDDF8",
                systemDnsServers = listOf(
                    DnsServerIdentity("10.1.10.2", "NetGate Local DoH Resolver", "VPN Tunnel DNS", isEncrypted = true)
                ),
                resolutionLatencyMs = 18,
                checkedAtMs = nowMs,
                summary = "All device DNS queries route through encrypted Cloudflare DoH with zero ISP leakage."
            )
            val leakState = DnsLeakTestState(
                isRunning = false,
                verdict = DnsLeakVerdict.NO_LEAK,
                summary = "Zero DNS leaks detected across IPv4, IPv6, and browser DoH probes.",
                dohLatencyMs = 18,
                egressIp = "172.64.36.1",
                egressColo = "SJC",
                activeSystemDns = listOf("10.1.10.2"),
                checks = listOf(
                    DnsLeakCheckItem("VPN DNS Tunnel Active", "Active DNS 10.1.10.2 routes through NetGate", DnsLeakCheckStatus.PASS),
                    DnsLeakCheckItem("IPv6 Leak Protection", "IPv6 DNS intercepted locally", DnsLeakCheckStatus.PASS),
                    DnsLeakCheckItem("Encrypted DoH Upstream", "Cloudflare 1.1.1.1 DoH responded in 18 ms", DnsLeakCheckStatus.PASS)
                ),
                lastTestedAtMs = nowMs
            )
            ScreenshotShell(
                destination = AppDestination.DNS,
                title = "DNS & Leak Test",
                subtitle = "Upstream DoH, Leak Test & Private DNS"
            ) {
                DnsProtectionContent(
                    connectionDetails = sampleConnection,
                    preferences = prefs,
                    isFirewallActive = true,
                    vpnRuntimeState = VpnRuntimeState(
                        isServiceRunning = true,
                        isTunnelActive = true,
                        isHttpsProxyActive = true,
                        blockedAdsCount = 342L
                    ),
                    probeState = DnsProbeState(isTesting = false, latencyMs = 18),
                    leakTestState = leakState,
                    dnsCheckState = dnsCheck,
                    onSelectUpstreamDns = {},
                    onSaveCustomDns = { _, _ -> },
                    onToggleDohUpstream = {},
                    onRunDnsCheck = {},
                    onRunDnsLeakTest = {},
                    onTestCloudflareDoh = {},
                    onSelectDpiMode = {},
                    onSelectDpiChunkSize = {},
                    onToggleDpiIdleKeepAlive = {},
                    onToggleBlockQuic = {},
                    onToggleAntiDnsPoison = {},
                    onSelectDnsTransportMode = {},
                    onFlushDpiCache = {},
                    onRequestEnableFirewall = {},
                    onCopiedHostname = {}
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ScreenshotShell(
        destination: AppDestination,
        title: String,
        subtitle: String,
        content: @Composable () -> Unit
    ) {
        NetGateTheme(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            blurEngineStyle = BlurEngineStyle.ACRYLIC_PRO,
            blurAnimationEnabled = false
        ) {
            FrostedBackdrop(
                isFirewallActive = true,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("screenshot_root")
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            title = {
                                Column {
                                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = {}) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                                }
                                IconButton(onClick = {}) {
                                    Icon(Icons.Rounded.Tune, contentDescription = "Settings")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        content()
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    FloatingFrostedNavBar(
                        currentDestination = destination,
                        isConnectionInfoOpen = false,
                        onSelectFirewall = {},
                        onSelectWebsites = {},
                        onSelectDataUsage = {},
                        onSelectDnsProtection = {}
                    )
                }
            }
        }
    }

    private fun captureScreen(file: File, content: @Composable () -> Unit) {
        var hostView: View? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            hostView = LocalView.current
            content()
        }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        lateinit var bitmap: Bitmap
        composeRule.runOnIdle {
            val view = requireNotNull(hostView) { "Expected host View" }
            val rootView = view.rootView
            val width = rootView.width.coerceAtLeast(824)
            val height = rootView.height.coerceAtLeast(1830)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            rootView.draw(canvas)
        }

        assertTrue("Expected non-empty bitmap for ${file.name}", bitmap.width > 0 && bitmap.height > 0)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Expected non-empty PNG file ${file.absolutePath}", file.exists() && file.length() > 1000L)
    }

    private fun resolveScreenshotsDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            if (dir != null && File(dir, "settings.gradle.kts").exists()) {
                return File(dir, "docs/screenshots")
            }
            dir = dir?.parentFile
        }
        return File("docs/screenshots")
    }
}
