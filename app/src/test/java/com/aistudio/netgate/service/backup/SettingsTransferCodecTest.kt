package com.aistudio.netgate.service.backup

import com.aistudio.netgate.data.local.AdFilterListEntity
import com.aistudio.netgate.data.local.AppNetworkRuleEntity
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Round-trip and validation behaviour of the JSON settings export. Robolectric supplies the real
 * org.json implementation, which plain JVM unit tests only stub.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsTransferCodecTest {

    private val appRules = listOf(
        AppNetworkRuleEntity("com.example.blocked", blockWifi = true, blockMobileData = true, updatedAt = 111L),
        AppNetworkRuleEntity("com.example.wifi", blockWifi = true, blockMobileData = false, updatedAt = 222L)
    )
    private val websiteRules = listOf(
        WebsiteRuleEntity("ads.example.com", WebsiteRuleType.BLOCK, isEnabled = true, createdAt = 10L),
        WebsiteRuleEntity("good.example.com", WebsiteRuleType.ALLOW, isEnabled = false, createdAt = 20L)
    )
    private val filterLists = listOf(
        AdFilterListEntity(
            id = "custom-1",
            title = "My list",
            description = "Custom subscription",
            sourceUrl = "https://example.com/list.txt",
            category = "Custom",
            isEnabled = true
        )
    )
    private val preferences = mapOf(
        "is_ad_blocker_enabled" to "b:true",
        "selected_upstream_dns" to "s:CLOUDFLARE_STANDARD",
        "https_proxy_port" to "i:8899"
    )

    @Test
    fun `a settings export round-trips`() {
        val json = SettingsTransferCodec.encode(appRules, websiteRules, filterLists, preferences, nowMillis = 42L)

        val payload = SettingsTransferCodec.decode(json).getOrThrow()

        assertEquals(appRules, payload.appRules)
        assertEquals(websiteRules, payload.websiteRules)
        assertEquals(filterLists, payload.adFilterLists)
        assertEquals(preferences, payload.preferences)
        assertEquals(1, JSONObject(json).getInt("schemaVersion"))
        assertEquals("netgate_settings", JSONObject(json).getString("format"))
    }

    @Test
    fun `unknown keys are ignored`() {
        val json = SettingsTransferCodec.encode(appRules, websiteRules, filterLists, preferences)
        val root = JSONObject(json)
        root.put("futureSection", "ignored")
        root.getJSONArray("appRules").getJSONObject(0).put("wireguardKey", "never imported")
        root.getJSONObject("preferences").put("hotspot_client_labels_set", "ss:10.0.0.2=Phone")

        val payload = SettingsTransferCodec.decode(root.toString()).getOrThrow()

        assertEquals(appRules.size, payload.appRules.size)
        assertEquals("com.example.blocked", payload.appRules.first().packageName)
        // The codec never drops a key on its own: the repository/preferences layer strips personal
        // keys, so this asserts the codec is neutral about unknown payloads.
        assertEquals(
            "ss:10.0.0.2=Phone",
            payload.preferences["hotspot_client_labels_set"]
        )
    }

    @Test
    fun `a newer schema version is rejected instead of half-applied`() {
        val root = JSONObject(SettingsTransferCodec.encode(appRules, websiteRules, filterLists, preferences))
        root.put("schemaVersion", 99)

        val result = SettingsTransferCodec.decode(root.toString())

        assertTrue(result.isFailure)
    }

    @Test
    fun `a missing or foreign format is rejected`() {
        assertTrue(SettingsTransferCodec.decode("""{"schemaVersion":1}""").isFailure)
        assertTrue(
            SettingsTransferCodec.decode("""{"format":"other_app","schemaVersion":1}""").isFailure
        )
        assertTrue(SettingsTransferCodec.decode("not json at all").isFailure)
    }

    @Test
    fun `malformed rows are skipped and domains normalised`() {
        val json = """
            {
              "format": "netgate_settings",
              "schemaVersion": 1,
              "appRules": [ {"packageName": ""}, {"packageName": "com.ok.app", "blockWifi": true} ],
              "websiteRules": [ {"domain": "Blocks.Example.COM "}, {"domain": ""} ],
              "adFilterLists": [ {"id": "", "sourceUrl": "https://x"} ]
            }
        """.trimIndent()

        val payload = SettingsTransferCodec.decode(json).getOrThrow()

        assertEquals(listOf("com.ok.app"), payload.appRules.map { it.packageName })
        assertEquals(listOf("blocks.example.com"), payload.websiteRules.map { it.domain })
        assertTrue(payload.adFilterLists.isEmpty())
        assertTrue(payload.preferences.isEmpty())
    }

    @Test
    fun `personal device keys are excluded from a settings export`() {
        val excluded = FirewallPreferences.PERSONAL_KEYS_EXCLUDED_FROM_EXPORT
        assertTrue(excluded.contains("hotspot_blocked_client_ips"))
        assertTrue(excluded.contains("hotspot_client_caps_set"))
        assertTrue(excluded.contains("hotspot_client_labels_set"))
        assertTrue(excluded.contains("saved_wifi_observed_set"))
        // A normal preference is still exported.
        assertFalse(excluded.contains("is_ad_blocker_enabled"))
    }
}
