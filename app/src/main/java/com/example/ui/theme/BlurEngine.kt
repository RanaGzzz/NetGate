package com.example.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BlurEngineStyle

data class BlurEngineConfig(
    val style: BlurEngineStyle = BlurEngineStyle.ACRYLIC_PRO,
    val animateMotion: Boolean = true
)

val LocalBlurEngineConfig = staticCompositionLocalOf { BlurEngineConfig() }

/**
 * Hardware-accelerated RenderEffect + spring-driven blur/tint compositor inspired by
 * MrSluffy's Blur Engine (blur-platform + blur-animation).
 *
 * On Android 12+ (API 31+), chains [AndroidRenderEffect.createBlurEffect] with a chromatic
 * saturation [AndroidRenderEffect.createColorFilterEffect] for rich Acrylic vibrancy without
 * per-frame allocations. Falls back cleanly on API 24-30.
 */
object BlurEngine {

    fun Modifier.blurEngineBackdrop(
        radius: Dp,
        saturation: Float
    ): Modifier = composed {
        val animatedRadius by animateDpAsState(
            targetValue = radius,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "blurEngineBackdropRadius"
        )
        val animatedSaturation by animateFloatAsState(
            targetValue = saturation,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "blurEngineSaturation"
        )

        if (animatedRadius <= 0.5.dp) {
            this
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radiusPx = with(LocalDensity.current) { animatedRadius.toPx().coerceAtLeast(1f) }
            this.graphicsLayer {
                renderEffect = createChainedAcrylicRenderEffect(radiusPx, animatedSaturation)
            }
        } else {
            this.blur(
                radius = animatedRadius,
                edgeTreatment = BlurredEdgeTreatment.Unbounded
            )
        }
    }

    fun Modifier.blurEngineModalDepth(active: Boolean): Modifier = composed {
        val config = LocalBlurEngineConfig.current
        val targetBlur = if (active && config.style != BlurEngineStyle.OFF) {
            config.style.modalBlurDp.dp
        } else {
            0.dp
        }
        val animatedBlur by animateDpAsState(
            targetValue = targetBlur,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "blurEngineModalDepth"
        )

        if (animatedBlur <= 0.5.dp) {
            this
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radiusPx = with(LocalDensity.current) { animatedBlur.toPx().coerceAtLeast(1f) }
            this.graphicsLayer {
                renderEffect = AndroidRenderEffect.createBlurEffect(
                    radiusPx,
                    radiusPx,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
        } else {
            this.blur(
                radius = animatedBlur,
                edgeTreatment = BlurredEdgeTreatment.Rectangle
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createChainedAcrylicRenderEffect(
        radiusPx: Float,
        saturation: Float
    ) = run {
        val blurNode = AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP
        )
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val colorNode = AndroidRenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(matrix)
        )
        AndroidRenderEffect.createChainEffect(blurNode, colorNode).asComposeRenderEffect()
    }
}
