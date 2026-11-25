package com.aistudio.netgate.data.network

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.aistudio.netgate.BuildConfig
import com.aistudio.netgate.data.model.OtaBuildInfo
import com.aistudio.netgate.data.model.OtaCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * OTA update channel.
 *
 * Primary source is a small JSON manifest (published next to the release APK) that describes the
 * exact build: version name, version code, build id and publish time. That makes it possible to
 * detect a *new build of the same version* — something a version-name-only GitHub comparison
 * cannot do. When the manifest is missing or unreachable, the GitHub Releases API is used as a
 * fallback and the installed package's `lastUpdateTime` is compared with the release timestamp.
 */
object OtaUpdateClient {

    const val DEFAULT_MANIFEST_URL =
        "https://raw.githubusercontent.com/RanaGzzz/NetGate/main/ota/update.json"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 12_000

    /** Minimum gap between an installed build and a remote publish time before it counts as newer. */
    private const val CLOCK_SKEW_MS = 60_000L

    suspend fun check(
        context: Context,
        manifestUrl: String = DEFAULT_MANIFEST_URL,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): Result<OtaCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val installedAt = installedLastUpdateTime(appContext)

            val manifestResult = runCatching {
                parseManifest(fetchJson(normalizeManifestUrl(manifestUrl)))
            }
            manifestResult.getOrNull()?.let { build ->
                return@runCatching evaluate(
                    build = build,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    installedAt = installedAt
                )
            }

            val github = GitHubReleaseUpdateChecker.checkForUpdates(currentVersionName).getOrThrow()
            val release = github.latestRelease
            val publishedMs = parseIsoMillis(release.publishedAtIso)
            val build = OtaBuildInfo(
                versionName = release.versionName,
                versionCode = github.latestVersionCode,
                buildId = release.tagName,
                publishedAtMs = publishedMs,
                publishedLabel = release.publishedAt,
                apkUrl = release.apkDownloadUrl.orEmpty(),
                notes = release.body,
                htmlUrl = release.htmlUrl,
                source = "GitHub Releases"
            )
            val versionNewer = github.isUpdateAvailable
            val sameVersionNewBuild = !versionNewer &&
                publishedMs > 0L &&
                installedAt > 0L &&
                publishedMs > installedAt + CLOCK_SKEW_MS
            OtaCheckResult(
                build = build,
                isUpdateAvailable = versionNewer || sameVersionNewBuild,
                reason = when {
                    versionNewer -> "Newer version ${build.versionName} is available"
                    sameVersionNewBuild -> "A newer build of ${build.versionName} was published"
                    else -> "You are on the latest build"
                }
            )
        }
    }

    /** Evaluates a manifest build against the running app + installed package timestamp. */
    fun evaluate(
        build: OtaBuildInfo,
        currentVersionName: String,
        currentVersionCode: Int,
        installedAt: Long
    ): OtaCheckResult {
        val versionNewer = build.versionCode > currentVersionCode ||
            GitHubReleaseUpdateChecker.compareVersions(build.versionName, currentVersionName) > 0
        val sameVersion = build.versionCode == currentVersionCode &&
            GitHubReleaseUpdateChecker.compareVersions(build.versionName, currentVersionName) == 0
        val sameVersionNewBuild = sameVersion &&
            build.publishedAtMs > 0L &&
            (installedAt <= 0L || build.publishedAtMs > installedAt + CLOCK_SKEW_MS)
        val differentBuildId = sameVersion &&
            build.buildId.isNotBlank() &&
            BuildConfig.GIT_COMMIT_SHA.isNotBlank() &&
            !build.buildId.equals(BuildConfig.GIT_COMMIT_SHA, ignoreCase = true) &&
            build.publishedAtMs > 0L &&
            (installedAt <= 0L || build.publishedAtMs > installedAt + CLOCK_SKEW_MS)
        val available = versionNewer || sameVersionNewBuild || differentBuildId
        return OtaCheckResult(
            build = build,
            isUpdateAvailable = available,
            reason = when {
                versionNewer -> "Version ${build.versionName} (build ${build.versionCode}) is available"
                available -> "A newer build of ${build.versionName} was published"
                else -> "You are on the latest build"
            }
        )
    }

    internal fun parseManifest(json: String): OtaBuildInfo {
        val obj = JSONObject(json)
        val apkUrl = obj.optString("apkUrl").trim()
        if (apkUrl.isEmpty()) throw IOException("OTA manifest has no apkUrl")
        val publishedIso = obj.optString("publishedAt").trim()
        return OtaBuildInfo(
            versionName = obj.optString("versionName", obj.optString("version", "0.0")).trim(),
            versionCode = obj.optInt("versionCode", 0),
            buildId = obj.optString("buildId", obj.optString("commit", "")).trim(),
            publishedAtMs = parseIsoMillis(publishedIso),
            publishedLabel = publishedIso.substringBefore('T'),
            apkUrl = apkUrl,
            sha256 = obj.optString("sha256", "").trim().lowercase(),
            notes = obj.optString("notes", "").trim(),
            htmlUrl = obj.optString("htmlUrl", "").trim(),
            source = "OTA manifest"
        )
    }

    fun parseIsoMillis(raw: String): Long {
        val clean = raw.trim()
        if (clean.isEmpty()) return 0L
        return runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(clean)?.time ?: 0L
        }.getOrElse {
            runCatching {
                val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateOnly.timeZone = TimeZone.getTimeZone("UTC")
                dateOnly.parse(clean.substringBefore('T'))?.time ?: 0L
            }.getOrDefault(0L)
        }
    }

    private fun installedLastUpdateTime(context: Context): Long {
        val pm = context.packageManager
        val info: PackageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull() ?: return 0L
        return info.lastUpdateTime
    }

    private fun normalizeManifestUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty()) DEFAULT_MANIFEST_URL else trimmed
    }

    private fun fetchJson(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "NetGate-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("OTA manifest returned HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }
}
