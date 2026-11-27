package com.aistudio.netgate

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.aistudio.netgate.service.NetGateVpnService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local-only diagnostics: the append-only log, its rotation, the promise that exception messages
 * are never written, and the crash handler chaining.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @After
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    private fun logFile(): File = File(File(context.filesDir, "logs"), "netgate.log")

    @Test
    fun `writes tagged lines into filesDir logs netgate log`() {
        AppLog.install(context)
        AppLog.info("Tunnel", "rebuilt")

        val file = logFile()
        assertTrue(file.isFile)
        val text = file.readText()
        assertTrue(text.contains("I/Tunnel: rebuilt"))
    }

    @Test
    fun `rotation keeps at most two files and caps the active log`() {
        AppLog.install(context)
        val filler = "x".repeat(4_000)
        repeat(200) { AppLog.info("Rotate", filler) }

        val dir = File(context.filesDir, "logs")
        val files = dir.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("netgate.log", "netgate.log.1"), files)
        assertTrue(logFile().length() <= 512L * 1024L)
        assertTrue(File(dir, "netgate.log.1").length() > 0L)
    }

    @Test
    fun `exception messages are never written to the log`() {
        AppLog.install(context)
        AppLog.error(
            "Tunnel",
            "establish failed",
            RuntimeException("secret.example.com 10.1.2.3 profile-key")
        )

        val text = logFile().readText()
        assertTrue(text.contains("E/Tunnel: establish failed"))
        assertTrue(text.contains("java.lang.RuntimeException"))
        assertFalse(text.contains("secret.example.com"))
        assertFalse(text.contains("10.1.2.3"))
        assertFalse(text.contains("profile-key"))
    }

    @Test
    fun `crash handler logs the crash and still chains to the previous handler`() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val chained = AtomicBoolean(false)
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> chained.set(true) }

        AppLog.install(context)
        val handler = requireNotNull(Thread.getDefaultUncaughtExceptionHandler())
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        assertTrue(chained.get())
        val text = logFile().readText()
        assertTrue(text.contains("F/Crash: uncaught exception"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertFalse(text.contains("boom"))
    }

    @Test
    fun `startOrReload swallows a refused service start`() {
        val refusing = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                throw IllegalStateException("background start not allowed")
            }
        }

        // Must not throw: the caller keeps its state and the user can retry.
        NetGateVpnService.startOrReload(refusing)
    }
}
