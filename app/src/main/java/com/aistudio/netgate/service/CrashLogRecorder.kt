package com.aistudio.netgate.service

import android.content.Context
import android.os.Build
import com.aistudio.netgate.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One recorded crash, already reduced to what the diagnostics screen shows.
 */
data class CrashReport(
    val timestampMs: Long,
    val headline: String,
    val detail: String
)

/**
 * Captures uncaught exceptions into plain text files so a crash can be read and copied out of the
 * app instead of only living in logcat. The handler always hands control back to the platform
 * handler, so crash behaviour (and Play Console reporting) is unchanged.
 */
object CrashLogRecorder {

    private const val DIR_NAME = "crash-logs"
    private const val FILE_PREFIX = "crash-"
    private const val MAX_REPORTS = 5
    private const val MAX_DETAIL_CHARS = 8_000
    private const val EXCEPTION_PREFIX = "exception: "

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashReport(appContext, thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** Writes a report for [throwable] and keeps only the newest [MAX_REPORTS] files. */
    internal fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable): File? {
        return runCatching {
            val dir = crashDir(context)
            val now = System.currentTimeMillis()
            val text = buildString {
                appendLine("NetGate crash report")
                appendLine("time: ${isoTimestamp(now)}")
                appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                if (BuildConfig.GIT_COMMIT_SHA.isNotBlank()) {
                    appendLine("commit: ${BuildConfig.GIT_COMMIT_SHA}")
                }
                appendLine(
                    "device: ${Build.MANUFACTURER} ${Build.MODEL} • " +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                )
                appendLine("thread: ${thread.name} (id ${thread.id})")
                appendLine("$EXCEPTION_PREFIX${throwable.javaClass.name}: ${throwable.message}")
                appendLine()
                appendLine(throwable.stackTraceToString().take(MAX_DETAIL_CHARS))
                throwable.cause?.let { cause ->
                    appendLine()
                    appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
                    appendLine(cause.stackTraceToString().take(MAX_DETAIL_CHARS))
                }
            }
            val file = File(dir, "$FILE_PREFIX$now.txt")
            file.writeText(text)
            trim(dir)
            AppLogCollector.crash(
                tag = "Crash",
                title = "App crash: ${throwable.javaClass.simpleName}",
                message = "${throwable.javaClass.name}: ${throwable.message ?: "Uncaught exception"} on ${thread.name}",
                stackTrace = text,
                timestampMs = now
            )
            file
        }.getOrNull()
    }

    /** Stored crashes, newest first. */
    fun reports(context: Context): List<CrashReport> {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
            .sortedByDescending { it.name }
            .mapNotNull { file ->
                runCatching {
                    val text = file.readText()
                    CrashReport(
                        timestampMs = timestampOf(file),
                        headline = text.lineSequence()
                            .firstOrNull { it.startsWith(EXCEPTION_PREFIX) }
                            ?.removePrefix(EXCEPTION_PREFIX)
                            ?: "Unknown crash",
                        detail = text
                    )
                }.getOrNull()
            }
    }

    fun clear(context: Context) {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun crashDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun trim(dir: File) {
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
            .sortedByDescending { it.name }
            .drop(MAX_REPORTS)
            .forEach { runCatching { it.delete() } }
    }

    private fun timestampOf(file: File): Long {
        return file.name.removePrefix(FILE_PREFIX).removeSuffix(".txt").toLongOrNull()
            ?: file.lastModified()
    }

    private fun isoTimestamp(timestampMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))
    }
}
