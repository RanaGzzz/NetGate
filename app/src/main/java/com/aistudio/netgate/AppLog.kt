package com.aistudio.netgate

import android.content.Context
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only, append-only diagnostics log (`filesDir/logs/netgate.log`, rotated at ~512 KB into a
 * single `netgate.log.1` backup, so at most two files exist) plus the default uncaught-exception
 * handler that feeds it.
 *
 * Nothing is ever uploaded. Only fixed, self-authored text is written: exception classes and stack
 * frames are recorded, exception *messages* are deliberately not, and callers must never pass
 * domains, IPs, package names, config text or keys.
 */
object AppLog {

    private const val DIR_NAME = "logs"
    private const val FILE_NAME = "netgate.log"
    private const val ROTATED_NAME = "$FILE_NAME.1"
    private const val MAX_BYTES = 512L * 1024L
    private const val MAX_FRAMES = 20

    private val lock = Any()

    @Volatile
    private var logDir: File? = null

    /** Installs the crash handler and points the log at [context]'s private files directory. */
    fun install(context: Context) {
        logDir = File(context.applicationContext.filesDir, DIR_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { recordCrash(thread, throwable) }
            // Chain to the previous handler so crash behaviour is unchanged.
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun info(tag: String, message: String) {
        append("I", tag, message)
    }

    fun warn(tag: String, message: String) {
        append("W", tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        append("E", tag, message)
        if (throwable != null) {
            append("E", tag, describe(throwable))
            throwable.cause?.let { append("E", tag, "caused by " + describe(it)) }
        }
    }

    /** The active log file, or null before [install]. */
    fun currentFile(): File? = logDir?.let { File(it, FILE_NAME) }

    private fun recordCrash(thread: Thread, throwable: Throwable) {
        append("F", "Crash", "uncaught exception on thread ${thread.name}")
        append("F", "Crash", describe(throwable))
        throwable.cause?.let { append("F", "Crash", "caused by " + describe(it)) }
    }

    /**
     * Class name plus stack frames only. The throwable message is skipped on purpose: messages can
     * carry hostnames, addresses or profile text, which must never reach the log file.
     */
    private fun describe(throwable: Throwable): String = buildString {
        append(throwable.javaClass.name)
        throwable.stackTrace.take(MAX_FRAMES).forEach { frame ->
            append("\n    at ").append(frame)
        }
    }

    private fun append(level: String, tag: String, message: String) {
        val dir = logDir ?: return
        val bytes = "${timestamp()} $level/$tag: $message\n".toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            runCatching {
                if (!dir.isDirectory) dir.mkdirs()
                val current = File(dir, FILE_NAME)
                if (current.length() + bytes.size > MAX_BYTES) {
                    val rotated = File(dir, ROTATED_NAME)
                    rotated.delete()
                    current.renameTo(rotated)
                }
                current.appendBytes(bytes)
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
