package com.example.data.network

import com.example.BuildConfig
import com.example.data.model.GitHubReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GitHubUpdateCheckResult(
    val latestRelease: GitHubReleaseInfo,
    val recentReleases: List<GitHubReleaseInfo>,
    val isUpdateAvailable: Boolean
)

object GitHubReleaseUpdateChecker {

    const val REPO_OWNER = "RanaGzzz"
    const val REPO_NAME = "NetGate"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    const val RELEASES_LIST_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=8"
    const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000

    suspend fun checkForUpdates(
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): Result<GitHubUpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val recentReleases = runCatching {
                val listJson = fetchJson(RELEASES_LIST_API_URL)
                parseReleasesListJson(listJson)
            }.getOrDefault(emptyList())

            val latestRelease = runCatching {
                val latestJson = fetchJson(LATEST_RELEASE_API_URL)
                parseReleaseJson(latestJson)
            }.getOrElse { err ->
                recentReleases.firstOrNull { !it.isPrerelease }
                    ?: recentReleases.firstOrNull()
                    ?: throw err
            }

            val mergedRecent = if (recentReleases.isEmpty()) {
                listOf(latestRelease)
            } else if (recentReleases.none { it.tagName == latestRelease.tagName }) {
                listOf(latestRelease) + recentReleases
            } else {
                recentReleases
            }

            val updateAvailable = compareVersions(
                remoteVersion = latestRelease.versionName,
                currentVersion = currentVersionName
            ) > 0

            GitHubUpdateCheckResult(
                latestRelease = latestRelease,
                recentReleases = mergedRecent,
                isUpdateAvailable = updateAvailable
            )
        }
    }

    internal fun parseReleaseJson(json: String): GitHubReleaseInfo {
        val obj = JSONObject(json)
        return parseReleaseObject(obj)
    }

    internal fun parseReleasesListJson(json: String): List<GitHubReleaseInfo> {
        val array = JSONArray(json)
        val result = ArrayList<GitHubReleaseInfo>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            result += parseReleaseObject(obj)
        }
        return result
    }

    private fun parseReleaseObject(obj: JSONObject): GitHubReleaseInfo {
        val tagName = obj.optString("tag_name").trim().ifEmpty {
            obj.optString("name").trim()
        }
        val cleanVersion = normalizeVersionString(tagName)
        val title = obj.optString("name").trim().ifEmpty { tagName.ifEmpty { "Release" } }
        val body = obj.optString("body").trim()
        val publishedAt = obj.optString("published_at").trim()
        val htmlUrl = obj.optString("html_url").trim().ifEmpty { RELEASES_PAGE_URL }
        val isPrerelease = obj.optBoolean("prerelease", false)

        val assets = obj.optJSONArray("assets")
        var apkDownloadUrl: String? = null
        var apkName: String? = null
        var apkSizeBytes = 0L

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name").trim()
                val downloadUrl = asset.optString("browser_download_url").trim()
                val contentType = asset.optString("content_type").trim()
                if (downloadUrl.isNotEmpty() && (
                        name.endsWith(".apk", ignoreCase = true) ||
                            contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
                        )
                ) {
                    apkDownloadUrl = downloadUrl
                    apkName = name.ifEmpty { "NetGate-$cleanVersion.apk" }
                    apkSizeBytes = asset.optLong("size", 0L).coerceAtLeast(0L)
                    break
                }
            }
        }

        return GitHubReleaseInfo(
            tagName = tagName.ifEmpty { cleanVersion },
            versionName = cleanVersion,
            title = title,
            body = body,
            publishedAt = formatPublishedDate(publishedAt),
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkDownloadUrl,
            apkName = apkName,
            apkSizeBytes = apkSizeBytes,
            isPrerelease = isPrerelease
        )
    }

    internal fun normalizeVersionString(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("NetGate-")
            .removePrefix("netgate-")
            .removePrefix("v")
            .removePrefix("V")
            .trim()
        return trimmed.ifEmpty { "0.0" }
    }

    /**
     * Compares two version strings semantically.
     * Returns > 0 if [remoteVersion] is newer than [currentVersion],
     * 0 if equal, < 0 if older.
     */
    fun compareVersions(remoteVersion: String, currentVersion: String): Int {
        val remoteNorm = normalizeVersionString(remoteVersion)
        val currentNorm = normalizeVersionString(currentVersion)

        val (remoteCore, remotePre) = splitCoreAndPrerelease(remoteNorm)
        val (currentCore, currentPre) = splitCoreAndPrerelease(currentNorm)

        val remoteParts = parseNumericParts(remoteCore)
        val currentParts = parseNumericParts(currentCore)
        val maxLen = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }

        // Core versions equal: stable > prerelease
        return when {
            remotePre == null && currentPre == null -> 0
            remotePre == null && currentPre != null -> 1
            remotePre != null && currentPre == null -> -1
            else -> (remotePre ?: "").compareTo(currentPre ?: "", ignoreCase = true)
        }
    }

    private fun splitCoreAndPrerelease(version: String): Pair<String, String?> {
        val clean = version.substringBefore('+')
        val dashIdx = clean.indexOf('-')
        return if (dashIdx >= 0) {
            clean.substring(0, dashIdx) to clean.substring(dashIdx + 1).ifBlank { null }
        } else {
            clean to null
        }
    }

    private fun parseNumericParts(core: String): List<Int> {
        return core.split('.')
            .mapNotNull { segment ->
                val digits = segment.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
            .ifEmpty { listOf(0) }
    }

    private fun formatPublishedDate(isoTimestamp: String): String {
        if (isoTimestamp.isBlank()) return ""
        val datePart = isoTimestamp.substringBefore('T').trim()
        return datePart.ifEmpty { isoTimestamp }
    }

    private fun fetchJson(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "NetGate-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val apiMsg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IOException(
                    if (!apiMsg.isNullOrBlank()) {
                        "GitHub API ($code): $apiMsg"
                    } else {
                        "GitHub Releases returned HTTP $code"
                    }
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
