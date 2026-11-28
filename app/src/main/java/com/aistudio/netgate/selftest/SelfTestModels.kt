package com.aistudio.netgate.selftest

/** Outcome of one self-test. */
enum class TestStatus { PASS, FAIL, WARN, SKIP }

/**
 * One finished check.
 *
 * [detail] is optional and only ever holds generated text (stack traces, synthetic values or
 * counts), never a real domain, IP address or another app's package name.
 */
data class TestResult(
    val id: String,
    val group: String,
    val name: String,
    val status: TestStatus,
    val durationMs: Long,
    val message: String,
    val detail: String? = null
) {
    val isFailing: Boolean get() = status == TestStatus.FAIL || status == TestStatus.WARN
}

/** Result a check body returns; the runner stamps id, group, name and duration. */
data class TestOutcome(
    val status: TestStatus,
    val message: String,
    val detail: String? = null
) {
    companion object {
        fun pass(message: String, detail: String? = null) = TestOutcome(TestStatus.PASS, message, detail)
        fun fail(message: String, detail: String? = null) = TestOutcome(TestStatus.FAIL, message, detail)
        fun warn(message: String, detail: String? = null) = TestOutcome(TestStatus.WARN, message, detail)
        fun skip(message: String, detail: String? = null) = TestOutcome(TestStatus.SKIP, message, detail)
    }
}

/** Status counts shown in the summary bar and the report. */
data class TestSummary(
    val pass: Int = 0,
    val fail: Int = 0,
    val warn: Int = 0,
    val skip: Int = 0
) {
    val total: Int get() = pass + fail + warn + skip

    companion object {
        fun of(results: List<TestResult>): TestSummary = TestSummary(
            pass = results.count { it.status == TestStatus.PASS },
            fail = results.count { it.status == TestStatus.FAIL },
            warn = results.count { it.status == TestStatus.WARN },
            skip = results.count { it.status == TestStatus.SKIP }
        )
    }
}
