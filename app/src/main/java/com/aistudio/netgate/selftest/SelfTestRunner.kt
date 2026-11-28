package com.aistudio.netgate.selftest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

/** One named check. [active] marks checks that must only run when the user opted in. */
class SelfTestCheck(
    val id: String,
    val group: String,
    val name: String,
    val active: Boolean = false,
    val body: suspend () -> TestOutcome
)

/**
 * Runs checks one at a time and emits a [TestResult] for each.
 *
 * Every check is wrapped in try/catch and [withTimeout], so a hanging engine, an exception or a
 * cancellation cannot take the caller down: the failure becomes a FAIL result carrying the stack
 * trace in [TestResult.detail]. Only genuine caller cancellation (the collecting coroutine is no
 * longer active) is rethrown.
 */
class SelfTestRunner(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val elapsedMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val maxDetailChars: Int = DEFAULT_MAX_DETAIL_CHARS
) {

    fun run(checks: List<SelfTestCheck>): Flow<TestResult> = flow {
        for (check in checks) {
            val startedAt = elapsedMs()
            val outcome = execute(check)
            emit(
                TestResult(
                    id = check.id,
                    group = check.group,
                    name = check.name,
                    status = outcome.status,
                    durationMs = (elapsedMs() - startedAt).coerceAtLeast(0L),
                    message = outcome.message,
                    detail = outcome.detail?.take(maxDetailChars)
                )
            )
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun execute(check: SelfTestCheck): TestOutcome {
        return try {
            withTimeout(timeoutMs) { check.body() }
        } catch (timeout: TimeoutCancellationException) {
            TestOutcome.fail("Timed out after $timeoutMs ms")
        } catch (cancel: CancellationException) {
            // A cancelled collector must stay cancelled; anything else that surfaces as a
            // CancellationException is treated as a failed check.
            if (!currentCoroutineContext().isActive) throw cancel
            TestOutcome.fail("Cancelled before it finished", cancel.stackTraceToString())
        } catch (error: Throwable) {
            TestOutcome.fail(
                message = "Failed: ${error::class.simpleName ?: "error"}",
                detail = error.stackTraceToString()
            )
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val DEFAULT_MAX_DETAIL_CHARS = 4_000
    }
}
