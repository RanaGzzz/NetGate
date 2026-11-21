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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BlurEngineStyle
import com.example.data.model.NetGateThemeStyle
import com.example.ui.theme.BlurEngine.expressiveLiquidTouch
import com.example.ui.theme.BlurEngine.liquidGlassRefractionEdge
import com.example.ui.theme.LiquidGlassPrismStops
import com.example.ui.theme.LocalBackdropLiquidState
import com.example.ui.theme.LocalBlurEngineConfig
import com.example.ui.theme.LocalExpressiveMotion
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalOverlayLiquidState
import com.example.ui.theme.LocalThemeStyle
import com.example.ui.theme.liquidFrostDp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zero-recomposition adaptive ambient backdrop:
 * - Liquid Glass: Luminous iridescent caustics & dispersion mesh
 * - Material 3 Expressive: High-chroma tonal washes & soft concentric halos
 * - Material You Lite: Lightweight clean tonal scrim with minimal GPU overdraw
 * - Cyber Slate: Signature ambient cyber aurora & telemetry rings
 */
@Composable
fun FrostedBackdrop(
    isFirewallActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val themeStyle = LocalThemeStyle.current
    val baseColor = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val primaryGlowState = animateColorAsState(
        targetValue = if (isFirewallActive) primary else secondary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "backdropPrimaryGlow"
    )

    val shouldAnimateDrift = blurConfig.animateMotion &&
        blurConfig.style != BlurEngineStyle.OFF &&
        themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE

    val phaseState: State<Float> = if (shouldAnimateDrift) {
        val infiniteTransition = rememberInfiniteTransition(label = "cyberAuroraDrift")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) 14_000 else 18_000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbPhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val intensityAlpha = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.10f
        NetGateThemeStyle.LIQUID_GLASS -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.16f
            BlurEngineStyle.SOFT_GLASS -> 0.28f
            BlurEngineStyle.ACRYLIC_PRO -> 0.36f
            BlurEngineStyle.ULTRA_FROST -> 0.44f
        }
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.14f
            BlurEngineStyle.SOFT_GLASS -> 0.26f
            BlurEngineStyle.ACRYLIC_PRO -> 0.34f
            BlurEngineStyle.ULTRA_FROST -> 0.40f
        }
        NetGateThemeStyle.NETGATE_DEFAULT -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.12f
            BlurEngineStyle.SOFT_GLASS -> 0.22f
            BlurEngineStyle.ACRYLIC_PRO -> 0.30f
            BlurEngineStyle.ULTRA_FROST -> 0.38f
        }
    }

    val backdropLiquidState = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) {
        rememberLiquidState()
    } else {
        null
    }
    val overlayLiquidState = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) {
        rememberLiquidState()
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (overlayLiquidState != null) {
                        Modifier.liquefiable(overlayLiquidState)
                    } else {
                        Modifier
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor)
                    .then(
                        if (backdropLiquidState != null) {
                            Modifier.liquefiable(backdropLiquidState)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE) {
                    LiteTonalBackdropCanvas(
                        primary = primaryGlowState.value,
                        secondary = secondary,
                        isDark = isDark
                    )
                } else {
                    AuroraBackdropCanvas(
                        phaseState = phaseState,
                        primaryGlowState = primaryGlowState,
                        secondary = secondary,
                        tertiary = tertiary,
                        intensityAlpha = intensityAlpha,
                        isDark = isDark,
                        showCyberRings = isFirewallActive && blurConfig.style != BlurEngineStyle.OFF,
                        isLiquidGlass = themeStyle == NetGateThemeStyle.LIQUID_GLASS
                    )
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdropLiquidState provides backdropLiquidState,
            LocalOverlayLiquidState provides overlayLiquidState
        ) {
            content()
        }
    }
}

@Composable
private fun LiteTonalBackdropCanvas(
    primary: Color,
    secondary: Color,
    isDark: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val topAlpha = if (isDark) 0.10f else 0.08f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    primary.copy(alpha = topAlpha),
                    secondary.copy(alpha = topAlpha * 0.45f),
                    Color.Transparent
                )
            )
        )
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
    showCyberRings: Boolean,
    isLiquidGlass: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Read animated states strictly inside DrawScope — zero recomposition overhead
        val phase = phaseState.value.toDouble()
        val primaryGlow = primaryGlowState.value

        val driftAmp = if (isLiquidGlass) 0.085f else 0.06f
        val driftX = sin(phase).toFloat() * size.width * driftAmp
        val driftY = cos(phase).toFloat() * size.height * (driftAmp * 0.85f)
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
    enableLiquidTouch: Boolean = false,
    liquidStateOverride: LiquidState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val themeStyle = LocalThemeStyle.current
    val motionSpec = LocalExpressiveMotion.current
    val activeLiquidState = liquidStateOverride ?: LocalBackdropLiquidState.current
    val useFletchLiquid = themeStyle == NetGateThemeStyle.LIQUID_GLASS && activeLiquidState != null

    val targetAlpha = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_YOU_LITE -> if (isDark) 0.90f else 0.94f
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> if (isDark) 0.84f else 0.90f
        NetGateThemeStyle.LIQUID_GLASS -> if (useFletchLiquid) {
            if (isDark) {
                (blurConfig.style.tintAlphaDark - 0.06f).coerceIn(0.42f, 0.72f)
            } else {
                (blurConfig.style.tintAlphaLight - 0.08f).coerceIn(0.48f, 0.78f)
            }
        } else {
            if (isDark) {
                (blurConfig.style.tintAlphaDark + 0.04f).coerceIn(0.54f, 0.86f)
            } else {
                (blurConfig.style.tintAlphaLight + 0.04f).coerceIn(0.64f, 0.92f)
            }
        }
        NetGateThemeStyle.NETGATE_DEFAULT -> if (isDark) {
            blurConfig.style.tintAlphaDark
        } else {
            blurConfig.style.tintAlphaLight
        }
    }

    val glassFill = tintColor ?: when (themeStyle) {
        NetGateThemeStyle.MATERIAL_YOU_LITE ->
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = targetAlpha)
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE ->
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = targetAlpha)
        else -> if (isDark) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = targetAlpha)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = targetAlpha)
        }
    }

    val sheenStrength = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.15f
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 0.55f
        NetGateThemeStyle.LIQUID_GLASS -> 1.35f
        NetGateThemeStyle.NETGATE_DEFAULT -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.25f
            BlurEngineStyle.SOFT_GLASS -> 0.75f
            BlurEngineStyle.ACRYLIC_PRO -> 1.0f
            BlurEngineStyle.ULTRA_FROST -> 1.25f
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val borderBrush = remember(isDark, borderAccent, sheenStrength, outlineColor, themeStyle) {
        when {
            themeStyle == NetGateThemeStyle.LIQUID_GLASS && borderAccent == null -> {
                Brush.linearGradient(
                    colors = LiquidGlassPrismStops.mapIndexed { idx, stop ->
                        val alpha = when {
                            idx == 4 -> if (isDark) 0.42f else 0.95f
                            isDark -> 0.34f
                            else -> 0.58f
                        }
                        stop.copy(alpha = alpha)
                    }
                )
            }
            themeStyle == NetGateThemeStyle.LIQUID_GLASS && borderAccent != null -> {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.58f else 0.95f),
                        borderAccent.copy(alpha = 0.85f),
                        LiquidGlassPrismStops[2].copy(alpha = 0.48f)
                    )
                )
            }
            borderAccent != null -> {
                Brush.linearGradient(
                    colors = listOf(
                        borderAccent.copy(alpha = (0.72f * sheenStrength).coerceIn(0.28f, 0.92f)),
                        borderAccent.copy(alpha = 0.24f)
                    )
                )
            }
            themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> {
                Brush.linearGradient(
                    colors = listOf(
                        outlineColor.copy(alpha = if (isDark) 0.36f else 0.42f),
                        outlineColor.copy(alpha = if (isDark) 0.20f else 0.26f)
                    )
                )
            }
            isDark -> {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.28f * sheenStrength).coerceIn(0.10f, 0.44f)),
                        Color.White.copy(alpha = 0.06f)
                    )
                )
            }
            else -> {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        outlineColor.copy(alpha = 0.34f)
                    )
                )
            }
        }
    }

    val sheenBrush = remember(isDark, sheenStrength, themeStyle) {
        if (themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE) {
            Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = (0.12f * sheenStrength).coerceIn(0.03f, 0.22f)),
                        Color.White.copy(alpha = 0.02f),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = (0.52f * sheenStrength).coerceIn(0.18f, 0.78f)),
                        Color.White.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                }
            )
        }
    }

    val glintModifier = if (motionSpec.enableCornerGlints) {
        Modifier.drawWithContent {
            drawContent()
            val glintAlpha = if (isDark) 0.42f else 0.78f
            val cornerClearance = (size.minDimension * 0.22f).coerceIn(10.dp.toPx(), 24.dp.toPx())
            val availableSpan = (size.width - cornerClearance * 2f).coerceAtLeast(0f)
            val glintLen = (availableSpan * 0.46f).coerceAtMost(72.dp.toPx())
            if (glintLen > 10f) {
                val edgeY = 2.dp.toPx()
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = glintAlpha), Color.Transparent),
                        start = Offset(cornerClearance, edgeY),
                        end = Offset(cornerClearance + glintLen, edgeY)
                    ),
                    start = Offset(cornerClearance, edgeY),
                    end = Offset(cornerClearance + glintLen, edgeY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val bottomY = size.height - edgeY
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = glintAlpha * 0.70f), Color.Transparent),
                        start = Offset(size.width - cornerClearance, bottomY),
                        end = Offset(size.width - cornerClearance - glintLen, bottomY)
                    ),
                    start = Offset(size.width - cornerClearance, bottomY),
                    end = Offset(size.width - cornerClearance - glintLen, bottomY),
                    strokeWidth = 1.3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    } else {
        Modifier
    }

    val cardFrostDp = blurConfig.style.liquidFrostDp()
    Surface(
        modifier = modifier
            .then(
                if (enableLiquidTouch) {
                    Modifier.expressiveLiquidTouch()
                } else {
                    Modifier
                }
            )
            .then(
                if (useFletchLiquid) {
                    Modifier.liquid(activeLiquidState!!) {
                        this.shape = shape
                        this.frost = cardFrostDp
                        this.refraction = 0.22f
                        this.curve = 0.28f
                        this.edge = 0.06f
                        this.dispersion = 0.025f
                        this.saturation = 1.18f
                        this.tint = glassFill
                    }
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = if (useFletchLiquid) Color.Transparent else glassFill,
        shadowElevation = elevation,
        border = BorderStroke(
            width = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) 1.25.dp else 1.dp,
            brush = borderBrush
        )
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (!useFletchLiquid) {
                        Modifier.liquidGlassRefractionEdge(
                            enabled = motionSpec.enableRefractionShader,
                            shape = shape
                        )
                    } else {
                        Modifier
                    }
                )
                .background(sheenBrush)
                .then(glintModifier),
            content = content
        )
    }
}
