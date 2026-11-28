package com.aistudio.netgate.selftest

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour of the sequential self-test runner: ordering, timeouts and exception capture. */
class SelfTestRunnerTest {

    private fun check(
        id: String,
        active: Boolean = false,
        body: suspend () -> TestOutcome
    ) = SelfTestCheck(id = id, group = "Group", name = "Check $id", active = active, body = body)

    @Test
    fun `results are emitted sequentially in list order`() = runBlocking {
        val runner = SelfTestRunner(timeoutMs = 2_000)
        val checks = (1..3).map { index ->
            check("c$index") {
                delay(5)
                TestOutcome.pass("ok $index")
            }
        }

        val results = runner.run(checks).toList()

        assertEquals(listOf("c1", "c2", "c3"), results.map { it.id })
        assertTrue(results.all { it.status == TestStatus.PASS })
        assertTrue(results.all { it.group == "Group" })
        assertTrue(results.all { it.durationMs >= 0L })
    }

    @Test
    fun `an exception becomes a fail with the stack trace in detail`() = runBlocking {
        val runner = SelfTestRunner(timeoutMs = 2_000)

        val results = runner.run(
            listOf(check("boom") { throw IllegalStateException("synthetic failure") })
        ).toList()

        val result = results.single()
        assertEquals(TestStatus.FAIL, result.status)
        assertTrue(result.message.contains("IllegalStateException"))
        val detail = result.detail
        assertNotNull(detail)
        assertTrue(detail!!.contains("synthetic failure"))
        assertTrue(detail.contains("SelfTestRunnerTest"))
    }

    @Test
    fun `a hanging check times out and the next one still runs`() = runBlocking {
        val runner = SelfTestRunner(timeoutMs = 60)
        val results = runner.run(
            listOf(
                check("slow") {
                    delay(10_000)
                    TestOutcome.pass("never reached")
                },
                check("fast") { TestOutcome.pass("ran") }
            )
        ).toList()

        assertEquals(2, results.size)
        assertEquals(TestStatus.FAIL, results[0].status)
        assertTrue(results[0].message.contains("Timed out"))
        assertEquals(TestStatus.PASS, results[1].status)
    }

    @Test
    fun `the runner never throws to the caller`() = runBlocking {
        val runner = SelfTestRunner(timeoutMs = 2_000)
        val results = runner.run(
            listOf(
                check("error") { throw AssertionError("boom") },
                check("failed") { TestOutcome.fail("expected failure") },
                check("warn") { TestOutcome.warn("expected warning") },
                check("skip") { TestOutcome.skip("expected skip") }
            )
        ).toList()

        assertEquals(4, results.size)
        assertEquals(
            listOf(TestStatus.FAIL, TestStatus.FAIL, TestStatus.WARN, TestStatus.SKIP),
            results.map { it.status }
        )
    }

    @Test
    fun `long details are truncated`() = runBlocking {
        val runner = SelfTestRunner(timeoutMs = 2_000, maxDetailChars = 32)
        val results = runner.run(
            listOf(check("verbose") { TestOutcome.fail("failed", "x".repeat(500)) })
        ).toList()

        assertEquals(32, results.single().detail?.length)
    }

    @Test
    fun `active checks are flagged for the caller to filter`() {
        val passive = SelfTestCheck("p", "Group", "Passive") { TestOutcome.pass("ok") }
        val active = SelfTestCheck("a", "Group", "Active", active = true) {
            TestOutcome.pass("ok")
        }

        assertTrue(active.active)
        assertTrue(!passive.active)
    }
}
