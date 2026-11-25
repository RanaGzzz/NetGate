package com.aistudio.netgate.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures the code paths NetGate runs on a cold start so ART can AOT-compile them
 * and the first frames after launch skip JIT warm-up.
 *
 * The startup path is marked as a startup profile: it seeds the dex layout so the
 * classes needed for the first frame are loaded early.
 */
@RunWith(AndroidJUnit4::class)
class NetGateBaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        // Every iteration must start from the launcher so the profile reflects a
        // genuine cold start rather than a warm resume.
        pressHome()
        startActivityAndWait()
        // The dashboard is composed behind the startup splash overlay; waiting for
        // it to settle profiles the composables and state pipelines behind it too.
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)
        device.waitForIdle(2_000)
    }

    private companion object {
        const val PACKAGE_NAME = "com.aistudio.netgate.xkqmpt"
    }
}
