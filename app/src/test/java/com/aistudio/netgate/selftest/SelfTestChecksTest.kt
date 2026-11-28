package com.aistudio.netgate.selftest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the deterministic part of the self-test registry against the real engines, so a broken
 * check body is caught here instead of showing up as a spurious FAIL on a device.
 */
@RunWith(RobolectricTestRunner::class)
class SelfTestChecksTest {

    private val deterministicGroups = setOf(
        SelfTestChecks.GROUP_POLICY,
        SelfTestChecks.GROUP_DNS,
        SelfTestChecks.GROUP_DPI,
        SelfTestChecks.GROUP_FIREWALL,
        SelfTestChecks.GROUP_WIREGUARD,
        SelfTestChecks.GROUP_AD_LISTS,
        SelfTestChecks.GROUP_PREFS,
        SelfTestChecks.GROUP_DATABASE,
        SelfTestChecks.GROUP_SERVICE
    )

    @Test
    fun `deterministic checks all pass`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val checks = SelfTestChecks.all(context).filter { it.group in deterministicGroups && !it.active }

        // A generous timeout here: this test validates the checks themselves, and a cold CI
        // runner can spend seconds opening the Room schema for the first time.
        val results = SelfTestRunner(timeoutMs = 30_000).run(checks).toList()

        assertEquals(checks.size, results.size)
        val failures = results.filter { it.status == TestStatus.FAIL }
        assertTrue(
            "Failing checks: " + failures.joinToString { "${it.id}: ${it.message} ${it.detail.orEmpty()}" },
            failures.isEmpty()
        )
    }

    @Test
    fun `active checks are opt-in`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val active = SelfTestChecks.all(context).filter { it.active }

        assertTrue(active.isNotEmpty())
        assertTrue(active.all { it.group == SelfTestChecks.GROUP_ACTIVE })
    }

    @Test
    fun `results never contain a real host or package name`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val checks = SelfTestChecks.all(context).filter { it.group in deterministicGroups && !it.active }
        val results = SelfTestRunner().run(checks).toList()

        // The suite may only mention its own synthetic example.test fixtures.
        val suspicious = results.filter { result ->
            val text = "${result.message} ${result.detail.orEmpty()}"
            Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").containsMatchIn(text)
        }
        assertTrue("Results leaked an IP-looking value: $suspicious", suspicious.isEmpty())
    }
}
