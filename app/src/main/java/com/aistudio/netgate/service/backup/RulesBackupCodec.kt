package com.aistudio.netgate.service.backup

import com.aistudio.netgate.data.local.WebsiteRuleEntity
import com.aistudio.netgate.data.local.WebsiteRuleType
import com.aistudio.netgate.data.model.AdFilterListItem
import com.aistudio.netgate.data.model.SmartProtectionProfile
import com.aistudio.netgate.data.model.WebsiteRuleItem
import com.aistudio.netgate.service.DnsPacketFilter
import org.json.JSONArray
import org.json.JSONObject

data class AdBlockBackupPayload(
    val profile: SmartProtectionProfile,
    val rules: List<WebsiteRuleEntity>,
    val enabledFilterIds: Set<String>
)

/**
 * Pure Kotlin codec for Import Rules, Export Rules, and Backup & Restore Rules
 * supporting Adblock (`||domain^`, `@@||domain^`), Hosts (`0.0.0.0 domain`),
 * plain domain lists, and structured JSON backups.
 */
object RulesBackupCodec {

    fun parseImportedRules(
        rawText: String,
        defaultIsWhitelist: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): List<WebsiteRuleEntity> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Allow importing JSON backup payload directly into Rules
        if (trimmed.startsWith("{")) {
            val decoded = decodeBackupJson(trimmed).getOrNull()
            if (decoded != null) {
                return decoded.rules
            }
        }

        val deduped = LinkedHashMap<String, WebsiteRuleEntity>()
        var currentDefaultWhitelist = defaultIsWhitelist

        for (rawLine in trimmed.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#") || line.startsWith("!")) {
                when {
                    line.contains("WHITELIST", ignoreCase = true) ||
                        line.contains("ALLOW", ignoreCase = true) -> currentDefaultWhitelist = true
                    line.contains("BLOCKLIST", ignoreCase = true) ||
                        line.contains("BLACKLIST", ignoreCase = true) ||
                        line.contains("BLOCK", ignoreCase = true) -> currentDefaultWhitelist = false
                }
                continue
            }

            var isAllow = currentDefaultWhitelist
            var candidate = line
            if (candidate.startsWith("@@")) {
                isAllow = true
                candidate = candidate.removePrefix("@@").trim()
            } else if (candidate.startsWith("ALLOW ", ignoreCase = true)) {
                isAllow = true
                candidate = candidate.substringAfter(' ').trim()
            } else if (candidate.startsWith("BLOCK ", ignoreCase = true)) {
                isAllow = false
                candidate = candidate.substringAfter(' ').trim()
            }

            if (candidate.startsWith("||")) {
                candidate = candidate.removePrefix("||")
                    .substringBefore('^')
                    .substringBefore('$')
            } else {
                val parts = candidate.split(Regex("\\s+"))
                if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::" || parts[0] == "::1")) {
                    candidate = parts[1]
                } else {
                    candidate = parts[0]
                }
            }

            candidate = candidate.removePrefix("*.").trim()
            val normalized = DnsPacketFilter.normalizeDomainInput(candidate) ?: continue
            deduped[normalized] = WebsiteRuleEntity(
                domain = normalized,
                ruleType = if (isAllow) WebsiteRuleType.ALLOW else WebsiteRuleType.BLOCK,
                isEnabled = true,
                createdAt = nowMillis,
                expiresAtMillis = 0L
            )
        }
        return deduped.values.toList()
    }

    fun exportRulesAsText(rules: List<WebsiteRuleItem>): String {
        val sb = StringBuilder()
        sb.appendLine("# NetGate Ad & Website Rules Export")
        sb.appendLine("# Whitelist")
        rules.filter { it.isWhitelist }.forEach { rule ->
            sb.appendLine("@@||${rule.domain}^")
        }
        sb.appendLine()
        sb.appendLine("# Blacklist")
        rules.filter { !it.isWhitelist }.forEach { rule ->
            sb.appendLine("||${rule.domain}^")
        }
        return sb.toString().trimEnd()
    }

    fun encodeBackupJson(
        rules: List<WebsiteRuleItem>,
        filterLists: List<AdFilterListItem>,
        profile: SmartProtectionProfile,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("format", "netgate_adblock_rules_v1")
        root.put("exportedAt", nowMillis)
        root.put("profile", profile.name)

        val rulesArr = JSONArray()
        for (rule in rules) {
            rulesArr.put(
                JSONObject()
                    .put("domain", rule.domain)
                    .put("ruleType", if (rule.isWhitelist) WebsiteRuleType.ALLOW else WebsiteRuleType.BLOCK)
                    .put("isEnabled", rule.isEnabled)
                    .put("createdAt", rule.createdAt)
                    .put("expiresAtMillis", rule.expiresAtMillis)
            )
        }
        root.put("rules", rulesArr)

        val enabledIds = JSONArray()
        for (list in filterLists) {
            if (list.isEnabled) enabledIds.put(list.id)
        }
        root.put("enabledFilterIds", enabledIds)
        return root.toString(2)
    }

    fun decodeBackupJson(rawJson: String): Result<AdBlockBackupPayload> = runCatching {
        val root = JSONObject(rawJson.trim())
        val profileName = root.optString("profile", SmartProtectionProfile.BALANCED.name)
        val profile = SmartProtectionProfile.entries.firstOrNull { it.name == profileName }
            ?: SmartProtectionProfile.BALANCED

        val rulesArr = root.optJSONArray("rules") ?: JSONArray()
        val parsedRules = ArrayList<WebsiteRuleEntity>(rulesArr.length())
        for (i in 0 until rulesArr.length()) {
            val obj = rulesArr.optJSONObject(i) ?: continue
            val rawDomain = obj.optString("domain", "").trim()
            val normalized = DnsPacketFilter.normalizeDomainInput(rawDomain) ?: continue
            val ruleType = if (obj.optString("ruleType", WebsiteRuleType.BLOCK) == WebsiteRuleType.ALLOW) {
                WebsiteRuleType.ALLOW
            } else {
                WebsiteRuleType.BLOCK
            }
            parsedRules += WebsiteRuleEntity(
                domain = normalized,
                ruleType = ruleType,
                isEnabled = obj.optBoolean("isEnabled", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                expiresAtMillis = obj.optLong("expiresAtMillis", 0L)
            )
        }

        val enabledIdsArr = root.optJSONArray("enabledFilterIds") ?: JSONArray()
        val enabledIds = LinkedHashSet<String>()
        for (i in 0 until enabledIdsArr.length()) {
            val id = enabledIdsArr.optString(i, "").trim()
            if (id.isNotEmpty()) enabledIds.add(id)
        }

        AdBlockBackupPayload(
            profile = profile,
            rules = parsedRules,
            enabledFilterIds = enabledIds
        )
    }
}
