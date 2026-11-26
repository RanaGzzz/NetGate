package com.aistudio.netgate.service

import android.content.Context
import android.os.Build
import android.os.Process
import com.aistudio.netgate.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class AppLogLevel(val label: String, val shortCode: String) {
    INFO("Info", "INFO"),
    WARN("Warn", "WARN"),
    ERROR("Error", "ERROR"),
    CRASH("Crash", "CRASH")
}

data class AppLogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: AppLogLevel,
    val tag: String,
    val title: String,
    val message: String,
    val stackTrace: String? = null
)

/**
 * Bounded in-memory runtime log collector that combines structured NetGate events (VPN connect/disconnect,
 * service start/stop, firewall rule updates, DNS/ad-blocker decisions, errors) with uncaught crash logs
 * and live app-scoped Logcat output (`--pid=<self>`).
 *
 * Chronologically ordered oldest-to-newest for real-time tailing in the diagnostics viewer.
 */
object AppLogCollector {

    const val MAX_ENTRIES = 1_500

    private val nextId = AtomicLong(1L)
    private val initialized = AtomicBoolean(false)
    private val logcatStarted = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private val buffer = ArrayDeque<AppLogEntry>(MAX_ENTRIES)
    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (initialized.compareAndSet(false, true)) {
            info(
                tag = "App",
                title = "NetGate process initialized",
                message = deviceAndAppMetadata()
            )
            seedStoredCrashes(appContext)
        }
        startLogcatCollectorIfSupported()
    }

    private fun seedStoredCrashes(context: Context) {
        scope.launch {
            val reports = CrashLogRecorder.reports(context)
            if (reports.isEmpty()) return@launch
            reports.asReversed().forEach { report ->
                val duplicate = synchronized(lock) {
                    buffer.any {
                        it.level == AppLogLevel.CRASH &&
                            it.timestampMs == report.timestampMs &&
                            it.title.contains(report.headline)
                    }
                }
                if (!duplicate) {
                    crash(
                        tag = "Crash",
                        title = "App crash: ${report.headline}",
                        message = report.headline,
                        stackTrace = report.detail,
                        timestampMs = report.timestampMs
                    )
                }
            }
        }
    }

    private fun startLogcatCollectorIfSupported() {
        val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        if (isRobolectric) return
        if (!logcatStarted.compareAndSet(false, true)) return

        scope.launch {
            runCatching {
                val pid = Process.myPid().toString()
                val process = ProcessBuilder(
                    "logcat",
                    "-v",
                    "threadtime",
                    "--pid=$pid",
                    "-T",
                    "120"
                ).redirectErrorStream(true).start()

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parseThreadtimeLine(line)?.let { (level, tag, msg) ->
                            // Skip noisy system framework chatter unless it is a warning or error
                            val isNetGateTag = tag.contains("NetGate", ignoreCase = true) ||
                                tag.contains("Firewall", ignoreCase = true) ||
                                tag.contains("Vpn", ignoreCase = true) ||
                                tag.contains("Dns", ignoreCase = true) ||
                                tag.contains("Dpi", ignoreCase = true) ||
                                tag.contains("AndroidRuntime", ignoreCase = true)
                            if (isNetGateTag || level != AppLogLevel.INFO) {
                                append(
                                    level = level,
                                    tag = tag.ifBlank { "Logcat" },
                                    title = tag.ifBlank { "System Log" },
                                    message = msg,
                                    stackTrace = null,
                                    timestampMs = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }
                runCatching { process.destroy() }
            }
        }
    }

    internal fun parseThreadtimeLine(line: String): Triple<AppLogLevel, String, String>? {
        // threadtime format: MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message
        val match = THREADTIME_REGEX.matchEntire(line.trim()) ?: return null
        val levelChar = match.groupValues[1]
        val tag = match.groupValues[2].trim()
        val message = match.groupValues[3].trim()
        if (message.isEmpty()) return null
        val level = when (levelChar) {
            "W" -> AppLogLevel.WARN
            "E", "A", "F" -> AppLogLevel.ERROR
            else -> AppLogLevel.INFO
        }
        return Triple(level, tag, message)
    }

    fun info(tag: String, title: String, message: String = title, timestampMs: Long = System.currentTimeMillis()) {
        append(
            level = AppLogLevel.INFO,
            tag = tag,
            title = title,
            message = message,
            stackTrace = null,
            timestampMs = timestampMs
        )
    }

    fun warn(tag: String, title: String, message: String = title, timestampMs: Long = System.currentTimeMillis()) {
        append(
            level = AppLogLevel.WARN,
            tag = tag,
            title = title,
            message = message,
            stackTrace = null,
            timestampMs = timestampMs
        )
    }

    fun error(
        tag: String,
        title: String,
        message: String = title,
        throwable: Throwable? = null,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        append(
            level = AppLogLevel.ERROR,
            tag = tag,
            title = title,
            message = message,
            stackTrace = throwable?.stackTraceToString()?.take(6_000),
            timestampMs = timestampMs
        )
    }

    fun crash(
        tag: String = "Crash",
        title: String,
        message: String,
        stackTrace: String? = null,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        append(
            level = AppLogLevel.CRASH,
            tag = tag,
            title = title,
            message = message,
            stackTrace = stackTrace,
            timestampMs = timestampMs
        )
    }

    private fun append(
        level: AppLogLevel,
        tag: String,
        title: String,
        message: String,
        stackTrace: String?,
        timestampMs: Long
    ) {
        val entry = AppLogEntry(
            id = nextId.getAndIncrement(),
            timestampMs = timestampMs,
            level = level,
            tag = tag.trim().ifBlank { "App" },
            title = title.trim(),
            message = message.trim(),
            stackTrace = stackTrace?.trim()?.takeIf { it.isNotEmpty() }
        )
        val snapshot = synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            buffer.toList()
        }
        _entries.value = snapshot
    }

    fun clear(context: Context? = null) {
        synchronized(lock) {
            buffer.clear()
        }
        _entries.value = emptyList()
        if (context != null) {
            CrashLogRecorder.clear(context)
        }
    }

    fun deviceAndAppMetadata(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val deviceName = listOf(manufacturer, model).filter { it.isNotEmpty() }.joinToString(" ").ifBlank { "Android Device" }
        return "NetGate v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • $deviceName • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    fun formatForExport(list: List<AppLogEntry> = entries.value): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("=== NetGate Diagnostics & Live App Log ===")
            appendLine(deviceAndAppMetadata())
            appendLine("Exported: ${fmt.format(Date())} • Entries: ${list.size}")
            appendLine("==========================================")
            if (list.isEmpty()) {
                appendLine("(No log entries recorded)")
            } else {
                list.forEach { entry ->
                    val ts = fmt.format(Date(entry.timestampMs))
                    appendLine("[$ts] [${entry.level.shortCode}] [${entry.tag}] ${entry.title} — ${entry.message}")
                    if (!entry.stackTrace.isNullOrBlank()) {
                        appendLine(entry.stackTrace)
                    }
                }
            }
        }
    }

    fun exportToCacheFile(context: Context, content: String): File? {
        return runCatching {
            val dir = File(context.cacheDir, "logs").apply { if (!exists()) mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "netgate-logs-$stamp.txt")
            file.writeText(content)
            // Keep cache directory tidy (latest 5 exports)
            dir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("netgate-logs-") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { runCatching { it.delete() } }
            file
        }.getOrNull()
    }

    private val THREADTIME_REGEX =
        Regex("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+([VDIWEAF])\\s+([^:]+):\\s*(.*)$")
}
