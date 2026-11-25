package com.aistudio.netgate.service.backup

import android.content.Context
import android.util.Base64
import com.aistudio.netgate.BuildConfig
import com.aistudio.netgate.data.local.AdFilterListEntity
import com.aistudio.netgate.data.local.AdvancedPreferences
import com.aistudio.netgate.data.local.AppNetworkRuleEntity
import com.aistudio.netgate.data.local.AppTimerEntity
import com.aistudio.netgate.data.local.FirewallPreferences
import com.aistudio.netgate.data.local.NetGateDatabase
import com.aistudio.netgate.data.local.VpnGuardProfileEntity
import com.aistudio.netgate.data.local.WebsiteRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted, password-protected backup of every user rule and setting NetGate keeps locally.
 *
 * The payload is a JSON document (settings + Room tables) encrypted with AES-256-GCM using a key
 * derived from the user password (PBKDF2-HMAC-SHA256, 120k iterations, random 16-byte salt and
 * 12-byte IV). The file is Base64 text tagged with a format magic so a wrong password or a
 * foreign file is rejected before anything is written.
 */
object EncryptedBackupManager {

    private const val MAGIC = "NETGATEBAK1"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    data class BackupSummary(
        val appRules: Int = 0,
        val websiteRules: Int = 0,
        val timers: Int = 0,
        val filterLists: Int = 0,
        val vpnProfiles: Int = 0,
        val settings: Int = 0,
        val advancedSettings: Int = 0
    )

    suspend fun export(context: Context, password: CharArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(password.size >= 8) { "Use a backup password with at least 8 characters" }
                val appContext = context.applicationContext
                val db = NetGateDatabase.getInstance(appContext)
                val firewallPrefs = FirewallPreferences(appContext)
                val advancedPrefs = AdvancedPreferences(appContext)

                val root = JSONObject()
                root.put("format", 1)
                root.put("createdAt", System.currentTimeMillis())
                root.put("appVersion", BuildConfig.VERSION_NAME)
                root.put("appVersionCode", BuildConfig.VERSION_CODE)
                root.put(
                    "settings",
                    JSONObject(firewallPrefs.exportRawSnapshot() as Map<*, *>)
                )
                root.put(
                    "advancedSettings",
                    JSONObject(advancedPrefs.exportRawSnapshot() as Map<*, *>)
                )

                root.put(
                    "appRules",
                    JSONArray().apply {
                        db.appNetworkRuleDao().getAllRulesOnce().forEach { rule ->
                            put(
                                JSONObject()
                                    .put("packageName", rule.packageName)
                                    .put("blockWifi", rule.blockWifi)
                                    .put("blockMobileData", rule.blockMobileData)
                                    .put("updatedAt", rule.updatedAt)
                            )
                        }
                    }
                )
                root.put(
                    "websiteRules",
                    JSONArray().apply {
                        db.websiteRuleDao().getAllRulesOnce().forEach { rule ->
                            put(
                                JSONObject()
                                    .put("domain", rule.domain)
                                    .put("ruleType", rule.ruleType)
                                    .put("isEnabled", rule.isEnabled)
                                    .put("createdAt", rule.createdAt)
                            )
                        }
                    }
                )
                root.put(
                    "timers",
                    JSONArray().apply {
                        db.appTimerDao().getAllTimersOnce().forEach { timer ->
                            put(
                                JSONObject()
                                    .put("packageName", timer.packageName)
                                    .put("blockWifiOnExpire", timer.blockWifiOnExpire)
                                    .put("blockMobileDataOnExpire", timer.blockMobileDataOnExpire)
                                    .put("durationMillis", timer.durationMillis)
                                    .put("expiresAtMillis", timer.expiresAtMillis)
                                    .put("createdAtMillis", timer.createdAtMillis)
                            )
                        }
                    }
                )
                root.put(
                    "filterLists",
                    JSONArray().apply {
                        db.adFilterListDao().getAllFilterListsOnce().forEach { list ->
                            put(
                                JSONObject()
                                    .put("id", list.id)
                                    .put("title", list.title)
                                    .put("description", list.description)
                                    .put("sourceUrl", list.sourceUrl)
                                    .put("category", list.category)
                                    .put("isEnabled", list.isEnabled)
                                    .put("domainCount", list.domainCount)
                                    .put("lastSyncedAt", list.lastSyncedAt)
                            )
                        }
                    }
                )
                root.put(
                    "vpnProfiles",
                    JSONArray().apply {
                        db.vpnGuardProfileDao().getAllProfilesOnce().forEach { profile ->
                            put(
                                JSONObject()
                                    .put("id", profile.id)
                                    .put("name", profile.name)
                                    .put("providerType", profile.providerType)
                                    .put("rawConfig", profile.rawConfig)
                                    .put("endpointHost", profile.endpointHost)
                                    .put("interfaceAddresses", profile.interfaceAddresses)
                                    .put("dnsServers", profile.dnsServers)
                                    .put("mtu", profile.mtu)
                                    .put("routingMode", profile.routingMode)
                                    .put("targetPackagesCsv", profile.targetPackagesCsv)
                                    .put("createdAt", profile.createdAt)
                                    .put("lastConnectedAt", profile.lastConnectedAt)
                            )
                        }
                    }
                )

                encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
            }
        }

    suspend fun import(context: Context, payload: String, password: CharArray): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                if (password.isEmpty()) throw IllegalArgumentException("Enter the backup password")
                val plain = decrypt(payload.trim(), password)
                val root = JSONObject(String(plain, Charsets.UTF_8))
                val db = NetGateDatabase.getInstance(appContext)

                val settings = root.optJSONObject("settings")
                if (settings != null) {
                    FirewallPreferences(appContext).importRawSnapshot(settings.toStringMap())
                }
                val advanced = root.optJSONObject("advancedSettings")
                if (advanced != null) {
                    AdvancedPreferences(appContext).importRawSnapshot(advanced.toStringMap())
                }

                val appRules = root.optJSONArray("appRules") ?: JSONArray()
                val appRuleEntities = ArrayList<AppNetworkRuleEntity>(appRules.length())
                for (i in 0 until appRules.length()) {
                    val obj = appRules.optJSONObject(i) ?: continue
                    val pkg = obj.optString("packageName").trim()
                    if (pkg.isEmpty()) continue
                    appRuleEntities += AppNetworkRuleEntity(
                        packageName = pkg,
                        blockWifi = obj.optBoolean("blockWifi", false),
                        blockMobileData = obj.optBoolean("blockMobileData", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                }
                if (appRuleEntities.isNotEmpty()) {
                    db.appNetworkRuleDao().upsertRules(appRuleEntities)
                }

                val websiteRules = root.optJSONArray("websiteRules") ?: JSONArray()
                val websiteEntities = ArrayList<WebsiteRuleEntity>(websiteRules.length())
                for (i in 0 until websiteRules.length()) {
                    val obj = websiteRules.optJSONObject(i) ?: continue
                    val domain = obj.optString("domain").trim()
                    if (domain.isEmpty()) continue
                    websiteEntities += WebsiteRuleEntity(
                        domain = domain,
                        ruleType = obj.optString("ruleType", "BLOCK"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                }
                if (websiteEntities.isNotEmpty()) {
                    db.websiteRuleDao().upsertRules(websiteEntities)
                }

                val timers = root.optJSONArray("timers") ?: JSONArray()
                val timerEntities = ArrayList<AppTimerEntity>(timers.length())
                for (i in 0 until timers.length()) {
                    val obj = timers.optJSONObject(i) ?: continue
                    val pkg = obj.optString("packageName").trim()
                    if (pkg.isEmpty()) continue
                    timerEntities += AppTimerEntity(
                        packageName = pkg,
                        blockWifiOnExpire = obj.optBoolean("blockWifiOnExpire", false),
                        blockMobileDataOnExpire = obj.optBoolean("blockMobileDataOnExpire", false),
                        durationMillis = obj.optLong("durationMillis", 0L),
                        expiresAtMillis = obj.optLong("expiresAtMillis", 0L),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                }
                if (timerEntities.isNotEmpty()) {
                    db.appTimerDao().upsertTimers(timerEntities)
                }

                val filterLists = root.optJSONArray("filterLists") ?: JSONArray()
                val filterEntities = ArrayList<AdFilterListEntity>(filterLists.length())
                for (i in 0 until filterLists.length()) {
                    val obj = filterLists.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    if (id.isEmpty()) continue
                    filterEntities += AdFilterListEntity(
                        id = id,
                        title = obj.optString("title", id),
                        description = obj.optString("description", ""),
                        sourceUrl = obj.optString("sourceUrl", ""),
                        category = obj.optString("category", "Custom"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        domainCount = obj.optInt("domainCount", 0),
                        lastSyncedAt = obj.optLong("lastSyncedAt", 0L)
                    )
                }
                if (filterEntities.isNotEmpty()) {
                    db.adFilterListDao().upsertFilterLists(filterEntities)
                }

                val vpnProfiles = root.optJSONArray("vpnProfiles") ?: JSONArray()
                var vpnCount = 0
                for (i in 0 until vpnProfiles.length()) {
                    val obj = vpnProfiles.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    if (id.isEmpty()) continue
                    db.vpnGuardProfileDao().upsertProfile(
                        VpnGuardProfileEntity(
                            id = id,
                            name = obj.optString("name", id),
                            providerType = obj.optString("providerType", "CUSTOM_WIREGUARD"),
                            rawConfig = obj.optString("rawConfig", ""),
                            endpointHost = obj.optString("endpointHost", ""),
                            interfaceAddresses = obj.optString("interfaceAddresses", ""),
                            dnsServers = obj.optString("dnsServers", "1.1.1.1"),
                            mtu = obj.optInt("mtu", 1280),
                            routingMode = obj.optString("routingMode", "WHOLE_SYSTEM"),
                            targetPackagesCsv = obj.optString("targetPackagesCsv", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            lastConnectedAt = obj.optLong("lastConnectedAt", 0L)
                        )
                    )
                    vpnCount++
                }

                BackupSummary(
                    appRules = appRuleEntities.size,
                    websiteRules = websiteEntities.size,
                    timers = timerEntities.size,
                    filterLists = filterEntities.size,
                    vpnProfiles = vpnCount,
                    settings = settings?.length() ?: 0,
                    advancedSettings = advanced?.length() ?: 0
                )
            }
        }

    private fun encrypt(plain: ByteArray, password: CharArray): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val keyBytes = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain)
        val combined = MAGIC.toByteArray(Charsets.US_ASCII) + salt + iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String, password: CharArray): ByteArray {
        val combined = runCatching { Base64.decode(payload, Base64.DEFAULT) }
            .getOrElse { throw IllegalArgumentException("This file is not a NetGate backup") }
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        if (combined.size < magic.size + SALT_BYTES + IV_BYTES + 16) {
            throw IllegalArgumentException("This file is not a NetGate backup")
        }
        if (!combined.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw IllegalArgumentException("This file is not a NetGate backup")
        }
        val saltStart = magic.size
        val ivStart = saltStart + SALT_BYTES
        val ctStart = ivStart + IV_BYTES
        val salt = combined.copyOfRange(saltStart, ivStart)
        val iv = combined.copyOfRange(ivStart, ctStart)
        val ciphertext = combined.copyOfRange(ctStart, combined.size)
        val keyBytes = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return runCatching { cipher.doFinal(ciphertext) }.getOrElse {
            throw IllegalArgumentException("Wrong password, or the backup file is damaged")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = optString(key, "")
            if (key.isNotBlank()) out[key] = value
        }
        return out
    }
}
