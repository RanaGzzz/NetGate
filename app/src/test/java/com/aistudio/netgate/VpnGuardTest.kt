package com.aistudio.netgate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.aistudio.netgate.data.model.ManagedApp
import com.aistudio.netgate.data.model.VpnGateServer
import com.aistudio.netgate.data.model.VpnGuardProviderType
import com.aistudio.netgate.data.model.VpnGuardRoutingMode
import com.aistudio.netgate.data.model.VpnGuardConnectionState
import com.aistudio.netgate.data.model.VpnGuardProtocol
import com.aistudio.netgate.data.model.VpnGuardSubTab
import com.aistudio.netgate.data.model.VpnGuardUiState
import com.aistudio.netgate.service.OpenVpnConfigHelper
import com.aistudio.netgate.service.VpnGateClient
import com.aistudio.netgate.service.VpnGuardManager
import com.aistudio.netgate.service.VpnGuardService
import com.aistudio.netgate.service.WarpConfigGenerator
import com.aistudio.netgate.service.WireGuardConfigHelper
import com.aistudio.netgate.ui.components.VpnGuardContent
import com.aistudio.netgate.ui.theme.NetGateTheme
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

    @Test
    fun `openVpnConfigHelper parses ovpn profile, credentials, and strips windows directives`() {
        val sampleOvpn = """
            client
            dev tun
            proto tcp
            remote vpn.example.org 443
            auth-user-pass
            dhcp-option DNS 1.1.1.1
            tun-mtu 1420
            register-dns
            block-outside-dns
            <ca>
            -----BEGIN CERTIFICATE-----
            TEST
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()

        assertTrue(OpenVpnConfigHelper.looksLikeOpenVpnConfig(sampleOvpn))
        assertTrue(OpenVpnConfigHelper.requiresAuthUserPass(sampleOvpn))

        val (wgConf, _) = WireGuardConfigHelper.generateTemplateConfig()
        assertFalse(OpenVpnConfigHelper.looksLikeOpenVpnConfig(wgConf))

        val profile = OpenVpnConfigHelper.parseToProfile(
            rawConfig = sampleOvpn,
            nameHint = "Proton-Free.ovpn",
            routingModeOverride = VpnGuardRoutingMode.SELECTED_APPS,
            targetPackagesOverride = setOf("com.whatsapp"),
            username = "alice",
            password = "secret"
        ).getOrThrow()

        assertEquals("Proton-Free", profile.name)
        assertEquals(VpnGuardProtocol.OPENVPN, profile.protocol)
        assertEquals(VpnGuardProviderType.CUSTOM_OPENVPN, profile.providerType)
        assertEquals("vpn.example.org:443 (tcp)", profile.endpointHost)
        assertEquals("1.1.1.1", profile.dnsServers)
        assertEquals(1420, profile.mtu)

        val (user, pass) = OpenVpnConfigHelper.extractCredentials(profile.rawConfig)
        assertEquals("alice", user)
        assertEquals("secret", pass)

        val runtimeCfg = OpenVpnConfigHelper.prepareRuntimeConfig(profile.rawConfig)
        assertFalse(runtimeCfg.contains("register-dns"))
        assertFalse(runtimeCfg.contains("block-outside-dns"))
        assertFalse(runtimeCfg.contains("netgate-user-b64"))
        assertTrue(runtimeCfg.contains("auth-user-pass"))
    }

    @Test
    fun `vpngate server converts directly to deterministic OpenVPN profile and renders in-app Connect`() {
        ShadowVpnService.setPrepareResult(null)
        val server = VpnGateServer(
            hostName = "vpn777",
            ip = "203.0.113.77",
            score = 900000L,
            pingMs = 19,
            speedBps = 72_000_000L,
            countryLong = "Japan",
            countryShort = "JP",
            numVpnSessions = 12,
            uptimeMs = 100000L,
            totalUsers = 300L,
            totalTrafficBytes = 500000L,
            logType = "2weeks",
            operatorName = "Tsukuba",
            message = "",
            ovpnConfig = "client\ndev tun\nproto udp\nremote 203.0.113.77 1195\n<ca>\ndummy\n</ca>"
        )

        val profile = OpenVpnConfigHelper.fromVpnGateServer(server)
        assertEquals("vpngate_203.0.113.77", profile.id)
        assertEquals(VpnGuardProtocol.OPENVPN, profile.protocol)
        assertEquals(VpnGuardProviderType.VPNGATE, profile.providerType)
        assertEquals("ovpn", profile.protocol.fileExtension)

        var connectedServer: VpnGateServer? = null
        composeRule.setContent {
            NetGateTheme {
                VpnGuardContent(
                    state = VpnGuardUiState(
                        selectedSubTab = VpnGuardSubTab.VPNGATE,
                        vpnGateServers = listOf(server)
                    ),
                    allManagedApps = emptyList(),
                    isLocalFirewallActive = false,
                    nowMillis = System.currentTimeMillis(),
                    loadIcon = { null },
                    onSelectSubTab = {},
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
                    onNotifyMessage = {},
                    onConnectVpnGateServer = { connectedServer = it }
                )
            }
        }

        composeRule.onNodeWithTag("vpngate_connect_203.0.113.77").performScrollTo().performClick()
        assertEquals(server.ip, connectedServer?.ip)
    }
}
