package com.aistudio.netgate.service.backup

import com.aistudio.netgate.data.local.AdFilterListEntity
import com.aistudio.netgate.data.local.AppNetworkRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.service.DnsPacketFilter
import org.json.JSONArray
import org.json.JSONObject

/** Counts of what an import applied, for the confirmation message. */
data class SettingsImportSummary(
    val appRules: Int,
    val websiteRules: Int,
    val adFilterLists: Int
)

/** Everything a settings export carries: rules, filter subscriptions and preference values. */
data class SettingsTransferPayload(
    val appRules: List<AppNetworkRuleEntity>,
    val websiteRules: List<WebsiteRuleEntity>,
    val adFilterLists: List<AdFilterListEntity>,
    val preferences: Map<String, String>
)

/**
 * Plain-JSON settings export/import used by the Storage Access Framework picker.
 *
 * Deliberately limited to what the user configured: per-app rules, website rules, filter
 * subscriptions and DataStore preferences. WireGuard/OpenVPN profiles, traffic history,
 * connection events and hotspot client identities are not part of the payload — the caller also
 * strips the personal preference keys before encoding.
 *
 * Unknown JSON keys are ignored on import, and a payload with a missing or newer
 * [SCHEMA_VERSION] is rejected rather than half-applied.
 */
object SettingsTransferCodec {

    const val FORMAT = "netgate_settings"
    const val SCHEMA_VERSION = 1

    fun encode(
        appRules: List<AppNetworkRuleEntity>,
        websiteRules: List<WebsiteRuleEntity>,
        adFilterLists: List<AdFilterListEntity>,
        preferences: Map<String, String>,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", nowMillis)

        val appArr = JSONArray()
        for (rule in appRules) {
            appArr.put(
                JSONObject()
                    .put("packageName", rule.packageName)
                    .put("blockWifi", rule.blockWifi)
                    .put("blockMobileData", rule.blockMobileData)
                    .put("updatedAt", rule.updatedAt)
            )
        }
        root.put("appRules", appArr)

        val websiteArr = JSONArray()
        for (rule in websiteRules) {
            websiteArr.put(
                JSONObject()
                    .put("domain", rule.domain)
                    .put("ruleType", rule.ruleType)
                    .put("isEnabled", rule.isEnabled)
                    .put("createdAt", rule.createdAt)
                    .put("expiresAtMillis", rule.expiresAtMillis)
            )
        }
        root.put("websiteRules", websiteArr)

        val listArr = JSONArray()
        for (list in adFilterLists) {
            listArr.put(
                JSONObject()
                    .put("id", list.id)
                    .put("title", list.title)
                    .put("description", list.description)
                    .put("sourceUrl", list.sourceUrl)
                    .put("category", list.category)
                    .put("isEnabled", list.isEnabled)
            )
        }
        root.put("adFilterLists", listArr)

        val prefsObj = JSONObject()
        for ((name, value) in preferences) {
            prefsObj.put(name, value)
        }
        root.put("preferences", prefsObj)

        return root.toString(2)
    }

    fun decode(rawJson: String): Result<SettingsTransferPayload> = runCatching {
        val root = JSONObject(rawJson.trim())
        val format = root.optString("format", "")
        require(format == FORMAT) { "This file is not a NetGate settings export." }
        val version = root.optInt("schemaVersion", -1)
        require(version in 1..SCHEMA_VERSION) {
            "Unsupported settings file version (found $version, supported up to $SCHEMA_VERSION)."
        }

        val appRules = ArrayList<AppNetworkRuleEntity>()
        val appArr = root.optJSONArray("appRules") ?: JSONArray()
        for (i in 0 until appArr.length()) {
            val obj = appArr.optJSONObject(i) ?: continue
            val packageName = obj.optString("packageName", "").trim()
            if (packageName.isEmpty()) continue
            appRules += AppNetworkRuleEntity(
                packageName = packageName,
                blockWifi = obj.optBoolean("blockWifi", false),
                blockMobileData = obj.optBoolean("blockMobileData", false),
                updatedAt = obj.optLong("updatedAt", 0L)
            )
        }

        val websiteRules = ArrayList<WebsiteRuleEntity>()
        val websiteArr = root.optJSONArray("websiteRules") ?: JSONArray()
        for (i in 0 until websiteArr.length()) {
            val obj = websiteArr.optJSONObject(i) ?: continue
            val domain = DnsPacketFilter.normalizeDomainInput(obj.optString("domain", "")) ?: continue
            websiteRules += WebsiteRuleEntity(
                domain = domain,
                ruleType = if (obj.optString("ruleType", WebsiteRuleType.BLOCK) == WebsiteRuleType.ALLOW) {
                    WebsiteRuleType.ALLOW
                } else {
                    WebsiteRuleType.BLOCK
                },
                isEnabled = obj.optBoolean("isEnabled", true),
                createdAt = obj.optLong("createdAt", 0L),
                expiresAtMillis = obj.optLong("expiresAtMillis", 0L)
            )
        }

        val adFilterLists = ArrayList<AdFilterListEntity>()
        val listArr = root.optJSONArray("adFilterLists") ?: JSONArray()
        for (i in 0 until listArr.length()) {
            val obj = listArr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val sourceUrl = obj.optString("sourceUrl", "").trim()
            if (id.isEmpty() || sourceUrl.isEmpty()) continue
            adFilterLists += AdFilterListEntity(
                id = id,
                title = obj.optString("title", id).trim(),
                description = obj.optString("description", "").trim(),
                sourceUrl = sourceUrl,
                category = obj.optString("category", "Custom").trim(),
                isEnabled = obj.optBoolean("isEnabled", false)
            )
        }

        val preferences = LinkedHashMap<String, String>()
        val prefsObj = root.optJSONObject("preferences") ?: JSONObject()
        for (name in prefsObj.keys()) {
            // A preference value is stored as the type-tagged string DataStoreBackupCodec writes.
            val value = prefsObj.optString(name, "")
            if (name.isNotBlank() && value.length >= 2) preferences[name] = value
        }

        SettingsTransferPayload(
            appRules = appRules,
            websiteRules = websiteRules,
            adFilterLists = adFilterLists,
            preferences = preferences
        )
    }
}
