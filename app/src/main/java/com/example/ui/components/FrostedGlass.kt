package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BlurEngineStyle
import com.example.ui.theme.LocalBlurEngineConfig
import com.example.ui.theme.LocalIsDarkTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zero-recomposition futuristic ambient aurora backdrop.
 * Animated phase and color states are read inside DrawScope so scrolling lists and
 * child Composables never recompose during background motion.
 */
@Composable
fun FrostedBackdrop(
    isFirewallActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val baseColor = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val primaryGlowState = animateColorAsState(
        targetValue = if (isFirewallActive) primary else secondary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "backdropPrimaryGlow"
    )

    val phaseState: State<Float> = if (blurConfig.animateMotion && blurConfig.style != BlurEngineStyle.OFF) {
        val infiniteTransition = rememberInfiniteTransition(label = "cyberAuroraDrift")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbPhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val intensityAlpha = when (blurConfig.style) {
        BlurEngineStyle.OFF -> 0.12f
        BlurEngineStyle.SOFT_GLASS -> 0.22f
        BlurEngineStyle.ACRYLIC_PRO -> 0.30f
        BlurEngineStyle.ULTRA_FROST -> 0.38f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        AuroraBackdropCanvas(
            phaseState = phaseState,
            primaryGlowState = primaryGlowState,
            secondary = secondary,
            tertiary = tertiary,
            intensityAlpha = intensityAlpha,
            isDark = isDark,
            showCyberRings = isFirewallActive && blurConfig.style != BlurEngineStyle.OFF
        )

        content()
    }
}

@Composable
private fun AuroraBackdropCanvas(
    phaseState: State<Float>,
    primaryGlowState: State<Color>,
    secondary: Color,
    tertiary: Color,
    intensityAlpha: Float,
    isDark: Boolean,
    showCyberRings: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Read animated states strictly inside DrawScope — zero recomposition overhead
        val phase = phaseState.value.toDouble()
        val primaryGlow = primaryGlowState.value

        val driftX = sin(phase).toFloat() * size.width * 0.06f
        val driftY = cos(phase).toFloat() * size.height * 0.05f
        val scaleBoost = if (isDark) 1.0f else 0.82f

        val topLeftCenter = Offset(
            x = size.width * 0.18f + driftX,
            y = size.height * 0.14f + driftY
        )
        val topLeftAlpha = intensityAlpha * scaleBoost
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to primaryGlow.copy(alpha = topLeftAlpha),
                    0.45f to primaryGlow.copy(alpha = topLeftAlpha * 0.42f),
                    0.82f to primaryGlow.copy(alpha = topLeftAlpha * 0.08f),
                    1.0f to Color.Transparent
                ),
                center = topLeftCenter,
                radius = size.maxDimension * 0.62f
            ),
            radius = size.maxDimension * 0.62f,
            center = topLeftCenter
        )

        val rightCenter = Offset(
            x = size.width * 0.88f - driftX * 0.9f,
            y = size.height * 0.36f + driftY * 1.1f
        )
        val rightAlpha = intensityAlpha * 0.82f * scaleBoost
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to secondary.copy(alpha = rightAlpha),
                    0.48f to secondary.copy(alpha = rightAlpha * 0.38f),
                    0.85f to secondary.copy(alpha = rightAlpha * 0.07f),
                    1.0f to Color.Transparent
                ),
                center = rightCenter,
                radius = size.maxDimension * 0.54f
            ),
            radius = size.maxDimension * 0.54f,
            center = rightCenter
        )

        val bottomCenter = Offset(
            x = size.width * 0.28f - driftY * 0.8f,
            y = size.height * 0.84f - driftX * 0.8f
        )
        val bottomAlpha = intensityAlpha * 0.84f * scaleBoost
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to tertiary.copy(alpha = bottomAlpha),
                    0.46f to tertiary.copy(alpha = bottomAlpha * 0.40f),
                    0.84f to tertiary.copy(alpha = bottomAlpha * 0.08f),
                    1.0f to Color.Transparent
                ),
                center = bottomCenter,
                radius = size.maxDimension * 0.58f
            ),
            radius = size.maxDimension * 0.58f,
            center = bottomCenter
        )

        if (showCyberRings) {
            val ringCenter = Offset(size.width * 0.86f, size.height * 0.11f)
            val pulse = ((sin(phase * 2.0) + 1.0) * 0.5).toFloat()
            val ringRadius = size.minDimension * (0.20f + 0.08f * pulse)
            drawCircle(
                color = primaryGlow.copy(alpha = (0.09f - 0.05f * pulse).coerceAtLeast(0.01f)),
                radius = ringRadius,
                center = ringCenter,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = secondary.copy(alpha = 0.045f),
                radius = size.minDimension * 0.34f,
                center = ringCenter,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tintColor: Color? = null,
    borderAccent: Color? = null,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val targetAlpha = if (isDark) {
        blurConfig.style.tintAlphaDark
    } else {
        blurConfig.style.tintAlphaLight
    }

    val glassFill = tintColor ?: if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = targetAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = targetAlpha)
    }

    val sheenStrength = when (blurConfig.style) {
        BlurEngineStyle.OFF -> 0.25f
        BlurEngineStyle.SOFT_GLASS -> 0.75f
        BlurEngineStyle.ACRYLIC_PRO -> 1.0f
        BlurEngineStyle.ULTRA_FROST -> 1.25f
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val borderBrush = remember(isDark, borderAccent, sheenStrength, outlineColor) {
        if (borderAccent != null) {
            Brush.linearGradient(
                colors = listOf(
                    borderAccent.copy(alpha = (0.72f * sheenStrength).coerceIn(0.25f, 0.92f)),
                    borderAccent.copy(alpha = 0.22f)
                )
            )
        } else if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.28f * sheenStrength).coerceIn(0.10f, 0.42f)),
                    Color.White.copy(alpha = 0.06f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.96f),
                    outlineColor.copy(alpha = 0.34f)
                )
            )
        }
    }

    val sheenBrush = remember(isDark, sheenStrength) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color.White.copy(alpha = (0.12f * sheenStrength).coerceIn(0.03f, 0.18f)),
                    Color.White.copy(alpha = 0.02f),
                    Color.Transparent
                )
            } else {
                listOf(
                    Color.White.copy(alpha = (0.52f * sheenStrength).coerceIn(0.18f, 0.72f)),
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent
                )
            }
        )
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = glassFill,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, borderBrush)
    ) {
        Box(
            modifier = Modifier.background(sheenBrush),
            content = content
        )
    }
}
