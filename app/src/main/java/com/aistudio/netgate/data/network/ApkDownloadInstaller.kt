package com.aistudio.netgate.data.network

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.aistudio.netgate.BuildConfig
import com.aistudio.netgate.service.UpdateInstallReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads an OTA APK into app cache and hands it to Android's package installer, so updates no
 * longer bounce the user out to a browser / GitHub release page.
 */
object ApkDownloadInstaller {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    suspend fun download(
        context: Context,
        url: String,
        expectedSha256: String = "",
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://", ignoreCase = true)) {
                "Update download must use HTTPS"
            }
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val safeName = url.substringAfterLast('/').substringBefore('?')
                .ifBlank { "netgate-${BuildConfig.VERSION_NAME}.apk" }
            val target = File(dir, safeName)
            val partial = File(dir, "$safeName.part")
            if (partial.exists()) partial.delete()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "NetGate-Android/${BuildConfig.VERSION_NAME}")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("Update download failed (HTTP $code)")
                val total = connection.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                onProgress(((copied * 100L) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                if (copied <= 0L) throw IOException("Downloaded update is empty")
                if (expectedSha256.isNotBlank()) {
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expectedSha256, ignoreCase = true)) {
                        partial.delete()
                        throw IOException("Update checksum mismatch — download rejected")
                    }
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                onProgress(100)
                target
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Installs the downloaded APK. Uses a PackageInstaller session on Android 8+ (the system shows
     * its own confirmation dialog) and falls back to a FileProvider view intent if that fails.
     */
    fun install(context: Context, apkFile: File): Result<Unit> = runCatching {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            throw IllegalArgumentException("Update file is missing")
        }
        // A checksum from a release page only proves the bytes we expected arrived. The signing
        // certificate is what actually stops a foreign-signer APK from replacing NetGate, so it is
        // required here for both the OTA and GitHub download paths.
        if (!isSignedBySameCertificate(context, apkFile)) {
            throw IllegalArgumentException("Update signature does not match the installed app")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("netgate-update.apk", 0L, apkFile.length()).use { out ->
                    apkFile.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val statusIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = UpdateInstallReceiver.ACTION_INSTALL_STATUS
                    putExtra(UpdateInstallReceiver.EXTRA_SESSION_ID, sessionId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                session.commit(pendingIntent.intentSender)
            }
            Unit
        } else {
            launchSystemInstaller(context, apkFile)
        }
    }.recoverCatching { err ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            launchSystemInstaller(context, apkFile)
        } else {
            throw err
        }
    }

    /** True when [apkFile] is signed by the same certificate set as the running app. */
    internal fun isSignedBySameCertificate(context: Context, apkFile: File): Boolean = runCatching {
        val pm = context.packageManager
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: return false
        val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val archiveSigners = signerDigests(archiveInfo) ?: return false
        val installedSigners = signerDigests(installedInfo) ?: return false
        archiveSigners.isNotEmpty() && archiveSigners == installedSigners
    }.getOrDefault(false)

    private fun signerDigests(info: PackageInfo): Set<String>? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return null
        return signatures
            .map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }
            .toSet()
    }

    private fun launchSystemInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
