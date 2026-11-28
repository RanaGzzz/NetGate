package com.example.selftest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.netgate.service.AppLogCollector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns one self-test run and its report. It never touches the firewall's ViewModel, so the suite
 * cannot interfere with the screen state of the main app.
 */
class SelfTestViewModel(application: Application) : AndroidViewModel(application) {

    private val _results = MutableStateFlow<List<TestResult>>(emptyList())
    val results: StateFlow<List<TestResult>> = _results.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _includeActive = MutableStateFlow(false)
    val includeActive: StateFlow<Boolean> = _includeActive.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var runJob: Job? = null

    fun setIncludeActive(enabled: Boolean) {
        if (_isRunning.value) return
        _includeActive.value = enabled
    }

    /**
     * Runs the suite. Refuses while another run or a speed test is in progress, because both
     * would fight over the same network path.
     */
    fun runAll(speedTestInProgress: Boolean) {
        if (_isRunning.value) return
        if (speedTestInProgress) {
            _statusMessage.value = "Wait for the speed test to finish before running the self-test"
            return
        }
        val context = getApplication<Application>()
        _statusMessage.value = null
        runJob = viewModelScope.launch {
            _isRunning.value = true
            _results.value = emptyList()
            try {
                val checks = SelfTestChecks.all(context)
                    .filter { _includeActive.value || !it.active }
                SelfTestRunner().run(checks).collect { result ->
                    _results.update { it + result }
                }
                _statusMessage.value = "Self-test finished"
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
    }

    fun summary(): TestSummary = TestSummary.of(_results.value)

    fun failuresOnlyText(): String = SelfTestReport.failuresOnly(_results.value)

    fun buildMarkdownReport(): String = SelfTestReport.markdown(
        metadata = SelfTestReportMetadata.current(),
        results = _results.value,
        logLines = recentLogLines()
    )

    fun buildJsonReport(): String = SelfTestReport.json(
        metadata = SelfTestReportMetadata.current(),
        results = _results.value,
        logLines = recentLogLines()
    )

    /**
     * Writes both report formats into `cacheDir/selftest` and returns the Markdown file, which is
     * the one shared through the app's FileProvider.
     */
    fun writeReportToCache(): File? = runCatching {
        val context = getApplication<Application>()
        val directory = File(context.cacheDir, "selftest").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val markdown = File(directory, "netgate-selftest-$stamp.md")
        val json = File(directory, "netgate-selftest-$stamp.json")
        markdown.writeText(buildMarkdownReport())
        json.writeText(buildJsonReport())
        markdown
    }.getOrNull()

    private fun recentLogLines(): List<String> {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        return AppLogCollector.entries.value
            .takeLast(MAX_LOG_LINES)
            .map { entry ->
                val time = formatter.format(Date(entry.timestampMs))
                "[$time] ${entry.level.name} ${entry.tag}: ${entry.title} — ${entry.message}"
            }
    }

    companion object {
        private const val MAX_LOG_LINES = 80
    }
}
