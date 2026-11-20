package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.data.network.SpeedTestPhase
import com.example.data.network.SpeedTestState
import com.example.data.network.VideoPlaybackEvaluator
import com.example.data.network.VideoPlaybackVerdict
import com.example.data.network.VideoResolutionTier
import com.example.ui.components.SpeedTestContent
import com.example.ui.theme.NetGateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlaybackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `4K fiber connection marks all tiers smooth`() {
        val report = VideoPlaybackEvaluator.evaluate(
            downloadMbps = 65.0,
            sustainedFloorMbps = 55.0,
            pingMs = 12,
            jitterMs = 4,
            videoStartupMs = 45
        )

        assertEquals(VideoResolutionTier.ULTRA_HD_4K, report.maxSmoothTier)
        assertEquals(5, report.tiers.size)
        assertTrue(report.tiers.all { it.verdict == VideoPlaybackVerdict.SMOOTH })
        assertEquals(45, report.videoStartupMs)
        assertTrue(report.stabilityPercent >= 85)
    }

    @Test
    fun `mid tier connection marks 1080p smooth and 2K borderline`() {
        val report = VideoPlaybackEvaluator.evaluate(
            downloadMbps = 15.0,
            sustainedFloorMbps = 11.5,
            pingMs = 24,
            jitterMs = 8,
            videoStartupMs = 95
        )

        assertEquals(VideoResolutionTier.FHD_1080P, report.maxSmoothTier)
        val byTier = report.tiers.associateBy { it.tier }
        assertEquals(VideoPlaybackVerdict.BUFFERING, byTier[VideoResolutionTier.ULTRA_HD_4K]?.verdict)
        assertEquals(VideoPlaybackVerdict.BORDERLINE, byTier[VideoResolutionTier.QHD_2K]?.verdict)
        assertEquals(VideoPlaybackVerdict.SMOOTH, byTier[VideoResolutionTier.FHD_1080P]?.verdict)
        assertEquals(VideoPlaybackVerdict.SMOOTH, byTier[VideoResolutionTier.HD_720P]?.verdict)
        assertEquals(VideoPlaybackVerdict.SMOOTH, byTier[VideoResolutionTier.SD_420P]?.verdict)
    }

    @Test
    fun `slow connection marks 420p smooth and 720p borderline`() {
        val report = VideoPlaybackEvaluator.evaluate(
            downloadMbps = 3.5,
            sustainedFloorMbps = 2.4,
            pingMs = 40,
            jitterMs = 15,
            videoStartupMs = 160
        )

        assertEquals(VideoResolutionTier.SD_420P, report.maxSmoothTier)
        val byTier = report.tiers.associateBy { it.tier }
        assertEquals(VideoPlaybackVerdict.BORDERLINE, byTier[VideoResolutionTier.HD_720P]?.verdict)
        assertEquals(VideoPlaybackVerdict.SMOOTH, byTier[VideoResolutionTier.SD_420P]?.verdict)
        assertEquals(VideoPlaybackVerdict.BUFFERING, byTier[VideoResolutionTier.FHD_1080P]?.verdict)
    }

    @Test
    fun `ultra slow connection returns null max smooth tier`() {
        val report = VideoPlaybackEvaluator.evaluate(
            downloadMbps = 0.7,
            sustainedFloorMbps = 0.5,
            pingMs = 110,
            jitterMs = 35,
            videoStartupMs = 320
        )

        assertNull(report.maxSmoothTier)
        assertTrue(report.tiers.all { it.verdict == VideoPlaybackVerdict.BUFFERING })
    }

    @Test
    fun `high jitter downgrades 4K and 2K to borderline despite fast download`() {
        val report = VideoPlaybackEvaluator.evaluate(
            downloadMbps = 50.0,
            sustainedFloorMbps = 45.0,
            pingMs = 30,
            jitterMs = 70,
            videoStartupMs = 120
        )

        assertEquals(VideoResolutionTier.FHD_1080P, report.maxSmoothTier)
        val byTier = report.tiers.associateBy { it.tier }
        assertEquals(VideoPlaybackVerdict.BORDERLINE, byTier[VideoResolutionTier.ULTRA_HD_4K]?.verdict)
        assertTrue(byTier[VideoResolutionTier.ULTRA_HD_4K]?.note?.contains("high jitter") == true)
        assertEquals(VideoPlaybackVerdict.BORDERLINE, byTier[VideoResolutionTier.QHD_2K]?.verdict)
        assertEquals(VideoPlaybackVerdict.SMOOTH, byTier[VideoResolutionTier.FHD_1080P]?.verdict)
    }

    @Test
    fun `speed test content displays all five video resolution tiers and badge`() {
        val report = VideoPlaybackEvaluator.evaluate(
            downloadMbps = 15.0,
            sustainedFloorMbps = 11.5,
            pingMs = 24,
            jitterMs = 8,
            videoStartupMs = 95
        )
        val state = SpeedTestState(
            phase = SpeedTestPhase.COMPLETED,
            progress = 1f,
            liveSpeedMbps = 15.0,
            pingMs = 24,
            jitterMs = 8,
            downloadMbps = 15.0,
            uploadMbps = 9.2,
            videoPlaybackReport = report,
            testedNetworkLabel = "Wi-Fi"
        )

        composeRule.setContent {
            NetGateTheme {
                SpeedTestContent(
                    state = state,
                    activeNetworkLabel = "Wi-Fi",
                    onStartTest = {},
                    onStopTest = {},
                    onRunVideoTest = {}
                )
            }
        }

        composeRule.onNodeWithTag("video_playback_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Video Playback Test").assertIsDisplayed()
        composeRule.onNodeWithTag("video_max_resolution_badge").assertTextContains("Up to 1080p")

        VideoResolutionTier.entries.forEach { tier ->
            composeRule.onNodeWithTag("video_tier_${tier.shortLabel.lowercase()}")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText(tier.shortLabel).assertIsDisplayed()
        }

        composeRule.onNodeWithTag("video_test_action_button")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
