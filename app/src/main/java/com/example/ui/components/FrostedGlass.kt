package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BlurEngineStyle
import com.example.ui.theme.BlurEngine.blurEngineBackdrop
import com.example.ui.theme.LocalBlurEngineConfig
import com.example.ui.theme.LocalIsDarkTheme
import kotlin.math.cos
import kotlin.math.sin

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

    val primaryGlow by animateColorAsState(
        targetValue = if (isFirewallActive) primary else secondary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "backdropPrimaryGlow"
    )

    val phase = if (blurConfig.animateMotion && blurConfig.style != BlurEngineStyle.OFF) {
        val infiniteTransition = rememberInfiniteTransition(label = "blurEngineOrbDrift")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbPhase"
        ).value
    } else {
        0f
    }

    val intensityAlpha = when (blurConfig.style) {
        BlurEngineStyle.OFF -> 0.14f
        BlurEngineStyle.SOFT_GLASS -> 0.24f
        BlurEngineStyle.ACRYLIC_PRO -> 0.34f
        BlurEngineStyle.ULTRA_FROST -> 0.44f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blurEngineBackdrop(
                    radius = blurConfig.style.backdropRadiusDp.dp,
                    saturation = blurConfig.style.saturationBoost
                )
        ) {
            val driftX = sin(phase.toDouble()).toFloat() * size.width * 0.055f
            val driftY = cos(phase.toDouble()).toFloat() * size.height * 0.045f

            val topLeftCenter = Offset(
                x = size.width * 0.16f + driftX,
                y = size.height * 0.14f + driftY
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = if (isDark) intensityAlpha else intensityAlpha * 0.85f),
                        Color.Transparent
                    ),
                    center = topLeftCenter,
                    radius = size.maxDimension * 0.58f
                ),
                radius = size.maxDimension * 0.58f,
                center = topLeftCenter
            )

            val rightCenter = Offset(
                x = size.width * 0.88f - driftX * 0.9f,
                y = size.height * 0.36f + driftY * 1.1f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondary.copy(alpha = if (isDark) intensityAlpha * 0.86f else intensityAlpha * 0.72f),
                        Color.Transparent
                    ),
                    center = rightCenter,
                    radius = size.maxDimension * 0.50f
                ),
                radius = size.maxDimension * 0.50f,
                center = rightCenter
            )

            val bottomCenter = Offset(
                x = size.width * 0.28f - driftY * 0.8f,
                y = size.height * 0.84f - driftX * 0.8f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = if (isDark) intensityAlpha * 0.88f else intensityAlpha * 0.75f),
                        Color.Transparent
                    ),
                    center = bottomCenter,
                    radius = size.maxDimension * 0.56f
                ),
                radius = size.maxDimension * 0.56f,
                center = bottomCenter
            )
        }

        content()
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
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "frostedSurfaceAlpha"
    )

    val targetFill = tintColor ?: if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = animatedAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = animatedAlpha)
    }
    val glassFill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "frostedSurfaceTint"
    )

    val sheenStrength = when (blurConfig.style) {
        BlurEngineStyle.OFF -> 0.25f
        BlurEngineStyle.SOFT_GLASS -> 0.75f
        BlurEngineStyle.ACRYLIC_PRO -> 1.0f
        BlurEngineStyle.ULTRA_FROST -> 1.25f
    }

    val borderBrush = if (borderAccent != null) {
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
                MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
            )
        )
    }

    val sheenBrush = Brush.verticalGradient(
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
