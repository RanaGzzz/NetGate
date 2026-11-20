package com.example.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BlurEngineStyle
import kotlin.math.roundToInt

data class BlurEngineConfig(
    val style: BlurEngineStyle = BlurEngineStyle.ACRYLIC_PRO,
    val animateMotion: Boolean = true
)

val LocalBlurEngineConfig = staticCompositionLocalOf { BlurEngineConfig() }

/**
 * Hardware-accelerated RenderEffect + spring-driven blur/tint compositor.
 * Caches RenderEffect instances across frames using quantized radius/saturation buckets
 * to eliminate GPU pipeline re-uploads and frame drops during animations.
 */
object BlurEngine {

    fun Modifier.blurEngineBackdrop(
        radius: Dp,
        saturation: Float
    ): Modifier = composed {
        if (radius <= 0.5.dp) {
            this
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val density = LocalDensity.current
            val radiusPx = with(density) { radius.toPx().coerceIn(1f, 96f) }
            val quantizedRadius = (radiusPx * 2f).roundToInt() / 2f
            val quantizedSat = (saturation * 50f).roundToInt() / 50f
            val cachedEffect = remember(quantizedRadius, quantizedSat) {
                createChainedAcrylicRenderEffect(quantizedRadius, quantizedSat)
            }
            this.graphicsLayer {
                renderEffect = cachedEffect
            }
        } else {
            this.blur(
                radius = radius,
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
            val quantizedRadius = (radiusPx).roundToInt().toFloat().coerceAtLeast(1f)
            val cachedEffect = remember(quantizedRadius) {
                AndroidRenderEffect.createBlurEffect(
                    quantizedRadius,
                    quantizedRadius,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
            this.graphicsLayer {
                renderEffect = cachedEffect
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
    ): RenderEffect {
        val blurNode = AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP
        )
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val colorNode = AndroidRenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(matrix)
        )
        return AndroidRenderEffect.createChainEffect(blurNode, colorNode).asComposeRenderEffect()
    }
}
