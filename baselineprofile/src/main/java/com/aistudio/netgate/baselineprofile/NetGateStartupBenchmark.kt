package com.aistudio.netgate.baselineprofile

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start and first-frame measurements for NetGate.
 *
 * The 3.9 release work (throttled analytics, one matcher build per sync, compact ruleset) is not
 * covered by any assertion, so nothing stops a future change from reintroducing the recomposition
 * storm it fixed. This benchmark turns that work into a number CI can compare.
 *
 * Run on a device or emulator: `:baselineprofile:connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class NetGateStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        // The firewall list is the launch destination; wait for it before stopping the trace.
        val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        device.wait(Until.hasObject(By.res("android:id/content")), 2_000)
    }

    private companion object {
        const val PACKAGE_NAME = "com.aistudio.netgate.xkqmpt"
    }
}
