package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.ManagedApp
import com.example.data.model.VpnGateServer
import com.example.data.model.VpnGuardProviderType
import com.example.data.model.VpnGuardRoutingMode
import com.example.data.model.VpnGuardConnectionState
import com.example.data.model.VpnGuardSubTab
import com.example.data.model.VpnGuardUiState
import com.example.service.VpnGateClient
import com.example.service.VpnGuardManager
import com.example.service.VpnGuardService
import com.example.service.WarpConfigGenerator
import com.example.service.WireGuardConfigHelper
import com.example.ui.components.VpnGuardContent
import com.example.ui.theme.NetGateTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVpnService
import java.nio.charset.StandardCharsets
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VpnGuardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `generate keypair template produces valid parseable WireGuard config`() {
        val (conf, pubKey) = WireGuardConfigHelper.generateTemplateConfig(
            endpoint = "de-fra.example.net:51820"
        )
        assertTrue(pubKey.length >= 40)

        val parsedResult = WireGuardConfigHelper.parseToProfile(
            rawConfig = conf,
            nameHint = "Germany WG"
        )
        assertTrue(parsedResult.isSuccess)
        val profile = parsedResult.getOrThrow()
        assertEquals("Germany WG", profile.name)
        assertEquals("de-fra.example.net:51820", profile.endpointHost)
        assertEquals(VpnGuardRoutingMode.WHOLE_SYSTEM, profile.routingMode)
    }

    @Test
    fun `split tunnel config builder switches cleanly between Whole System and Selected Apps`() {
        val (baseConf, _) = WireGuardConfigHelper.generateTemplateConfig()
        val parsed = WireGuardConfigHelper.parseToProfile(
            rawConfig = """
                [Interface]
                Jc = 4
                Jmin = 40
                ${baseConf.substringAfter("[Interface]").trim()}
            """.trimIndent(),
            nameHint = "Proton Free"
        ).getOrThrow()

        val appScoped = parsed.copy(
            routingMode = VpnGuardRoutingMode.SELECTED_APPS,
            targetPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
        )
        val wgConfig = WireGuardConfigHelper.buildWireGuardConfig(appScoped).getOrThrow()
        assertEquals(
            setOf("com.whatsapp", "com.whatsapp.w4b"),
            wgConfig.`interface`.includedApplications
        )
        assertTrue(wgConfig.`interface`.excludedApplications.isEmpty())

        val wholeSystem = WireGuardConfigHelper.buildWireGuardConfig(
            parsed.copy(routingMode = VpnGuardRoutingMode.WHOLE_SYSTEM, targetPackages = emptySet())
        ).getOrThrow()
        assertTrue(wholeSystem.`interface`.includedApplications.isEmpty())
        assertTrue(wholeSystem.`interface`.excludedApplications.isEmpty())
    }

    @Test
    fun `warp registration json parses into Cloudflare WARP WireGuard profile`() {
        val (privKey, pubKey) = WireGuardConfigHelper.generateKeyPair()
        assertNotEquals(privKey, pubKey)

        val sampleJson = """
            {
              "id": "test-device",
              "config": {
                "client_id": "test",
                "interface": {
                  "addresses": {
                    "v4": "172.16.0.2",
                    "v6": "2606:4700:110:8a36:1234::2"
                  }
                },
                "peers": [
                  {
                    "public_key": "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
                    "endpoint": {
                      "v4": "162.159.192.1:0",
                      "host": "engage.cloudflareclient.com:2408"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val profile = WarpConfigGenerator.parseWarpRegistrationJson(
            jsonText = sampleJson,
            privateKeyBase64 = privKey,
            profileName = "WARP WhatsApp",
            routingMode = VpnGuardRoutingMode.SELECTED_APPS,
            targetPackages = setOf("com.whatsapp")
        ).getOrThrow()

        assertEquals(VpnGuardProviderType.CLOUDFLARE_WARP, profile.providerType)
        assertEquals("engage.cloudflareclient.com:2408", profile.endpointHost)
        assertEquals(VpnGuardRoutingMode.SELECTED_APPS, profile.routingMode)
        assertEquals(setOf("com.whatsapp"), profile.targetPackages)
        assertTrue(profile.rawConfig.contains("PrivateKey = $privKey"))
    }

    @Test
    fun `vpngate csv parser decodes valid OpenVPN profiles and skips invalid lines`() {
        val validOvpn = """
            client
            dev tun
            proto udp
            remote 203.0.113.10 1195
            <ca>
            dummy
            </ca>
        """.trimIndent()
        val b64Valid = Base64.getEncoder().encodeToString(validOvpn.toByteArray(StandardCharsets.UTF_8))
        val invalidB64 = Base64.getEncoder().encodeToString("not a valid config".toByteArray(StandardCharsets.UTF_8))

        val csv = """
            *vpn_servers
            #HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
            public-vpn-101,203.0.113.10,1920000,24,84500000,Japan,JP,42,3600000,1500,987654321,2weeks,"Tsukuba Univ, Relay","",${b64Valid}
            bad-vpn-102,198.51.100.2,1000,99,100000,Germany,DE,1,1000,10,1000,2weeks,BadOp,"",${invalidB64}
            *
        """.trimIndent()

        val servers = VpnGateClient.parseCsv(csv)
        assertEquals(1, servers.size)
        val jp = servers.first()
        assertEquals("public-vpn-101", jp.hostName)
        assertEquals("203.0.113.10", jp.ip)
        assertEquals("JP", jp.countryShort)
        assertEquals("Tsukuba Univ, Relay", jp.operatorName)
        assertTrue(jp.ovpnConfig.contains("remote 203.0.113.10 1195"))
        assertFalse(jp.ovpnConfig.contains("not a valid"))
    }

    @Test
    fun `vpn guard screen displays profiles and switches to VPNGate tab`() {
        val (sampleConf, _) = WireGuardConfigHelper.generateTemplateConfig("nl.example.org:51820")
        val profile = WireGuardConfigHelper.parseToProfile(sampleConf, "Proton NL").getOrThrow()
        val gateServer = VpnGateServer(
            hostName = "vpn999",
            ip = "203.0.113.99",
            score = 500000L,
            pingMs = 31,
            speedBps = 55_000_000L,
            countryLong = "Germany",
            countryShort = "DE",
            numVpnSessions = 18,
            uptimeMs = 120000L,
            totalUsers = 500L,
            totalTrafficBytes = 1_000_000L,
            logType = "2weeks",
            operatorName = "University Relay",
            message = "",
            ovpnConfig = "client\ndev tun\nremote 203.0.113.99 443"
        )

        var activeTab = VpnGuardSubTab.PROFILES

        composeRule.setContent {
            NetGateTheme {
                VpnGuardContent(
                    state = VpnGuardUiState(
                        selectedSubTab = activeTab,
                        profiles = listOf(profile),
                        selectedProfileId = profile.id,
                        vpnGateServers = listOf(gateServer)
                    ),
                    allManagedApps = listOf(
                        ManagedApp("com.whatsapp", "WhatsApp", false, true, false, false)
                    ),
                    isLocalFirewallActive = false,
                    nowMillis = System.currentTimeMillis(),
                    loadIcon = { null },
                    onSelectSubTab = { activeTab = it },
                    onSelectProfile = {},
                    onImportWireGuardConfig = { _, _, _, _ -> },
                    onGenerateWarpProfile = { _, _, _, _ -> },
                    onGenerateKeypairTemplate = {},
                    onUpdateProfileRouting = { _, _, _ -> },
                    onDeleteProfile = {},
                    onConnectProfile = {},
                    onDisconnect = {},
                    onRefreshVpnGate = {},
                    onVpnGateSearchChange = {},
                    onVpnGateSortChange = {},
                    onNotifyMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("vpn_guard_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("vpn_guard_generate_warp_button").assertIsDisplayed()
        composeRule.onNodeWithTag("vpn_guard_screen").performScrollToNode(hasText("Proton NL"))
        composeRule.onNodeWithText("Proton NL").assertIsDisplayed()
        composeRule.onNodeWithTag("vpn_guard_screen").performScrollToIndex(1)
        composeRule.onNodeWithTag("vpn_guard_tab_vpngate").performScrollTo().performClick()
        assertEquals(VpnGuardSubTab.VPNGATE, activeTab)
    }

    @Test
    fun `warp registration json normalizes zero port to 2408`() {
        val (privKey, _) = WireGuardConfigHelper.generateKeyPair()
        val sampleZeroPortJson = """
            {
              "id": "test-zero-port",
              "config": {
                "interface": {
                  "addresses": {
                    "v4": "172.16.0.2"
                  }
                },
                "peers": [
                  {
                    "public_key": "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
                    "endpoint": {
                      "v4": "162.159.192.1:0",
                      "host": "engage.cloudflareclient.com:0"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val profile = WarpConfigGenerator.parseWarpRegistrationJson(
            jsonText = sampleZeroPortJson,
            privateKeyBase64 = privKey
        ).getOrThrow()

        assertEquals("engage.cloudflareclient.com:2408", profile.endpointHost)
        assertTrue(profile.rawConfig.contains("Endpoint = engage.cloudflareclient.com:2408"))
    }

    @Test
    fun `replaceEndpointHost swaps host cleanly while preserving port`() {
        val (baseConf, _) = WireGuardConfigHelper.generateTemplateConfig("engage.cloudflareclient.com:2408")
        val v4Swapped = WireGuardConfigHelper.replaceEndpointHost(baseConf, "162.159.192.1")
        assertTrue(v4Swapped.contains("Endpoint = 162.159.192.1:2408"))

        val v6Swapped = WireGuardConfigHelper.replaceEndpointHost(baseConf, "2606:4700:d0::a29f:c001")
        assertTrue(v6Swapped.contains("Endpoint = [2606:4700:d0::a29f:c001]:2408"))
    }

    @Test
    fun `vpn guard connect with uninstalled split-tunnel apps fails cleanly without crashing`() = runBlocking {
        ShadowVpnService.setPrepareResult(null)
        val context = RuntimeEnvironment.getApplication()
        val (conf, _) = WireGuardConfigHelper.generateTemplateConfig("162.159.192.1:2408")
        val profile = WireGuardConfigHelper.parseToProfile(
            rawConfig = conf,
            nameHint = "WhatsApp Split Test",
            routingModeOverride = VpnGuardRoutingMode.SELECTED_APPS,
            targetPackagesOverride = setOf("com.nonexistent.uninstalled.whatsapp.clone")
        ).getOrThrow()

        val result = VpnGuardManager.connect(context, profile)
        assertTrue(result.isFailure)
        val runtime = VpnGuardManager.runtimeState.value
        assertEquals(VpnGuardConnectionState.ERROR, runtime.connectionState)
        assertFalse(runtime.errorMessage.isNullOrBlank())

        // Stopping VpnGuardService cleanly even when never foregrounded must never throw
        VpnGuardService.stop(context)
        VpnGuardManager.disconnect(context, restoreFirewallIfPaused = false)
        assertEquals(VpnGuardConnectionState.DISCONNECTED, VpnGuardManager.runtimeState.value.connectionState)
    }
}
