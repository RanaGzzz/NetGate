package com.aistudio.netgate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.BackgroundAnimationStyle
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.NetGateThemeStyle
import com.aistudio.netgate.ui.theme.BlurEngine.blurEngineBackdrop
import com.aistudio.netgate.ui.theme.BlurEngine.expressiveLiquidTouch
import com.aistudio.netgate.ui.theme.BlurEngine.frostedChromaticEdge
import com.aistudio.netgate.ui.theme.BlurEngine.liquidGlassRefractionEdge
import com.aistudio.netgate.ui.theme.LiquidGlassAqua
import com.aistudio.netgate.ui.theme.LiquidGlassCyan
import com.aistudio.netgate.ui.theme.LiquidGlassPrismStops
import com.aistudio.netgate.ui.theme.LiquidGlassPrismViolet
import com.aistudio.netgate.ui.theme.LocalBackdropLiquidState
import com.aistudio.netgate.ui.theme.LocalBlurEngineConfig
import com.aistudio.netgate.ui.theme.LocalDynamicColorEnabled
import com.aistudio.netgate.ui.theme.LocalExpressiveMotion
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import com.aistudio.netgate.ui.theme.LocalOverlayLiquidState
import com.aistudio.netgate.ui.theme.LocalThemeStyle
import com.aistudio.netgate.ui.theme.liquidFrostDp
import com.aistudio.netgate.ui.theme.rememberLifecycleMotionProgress
import com.aistudio.netgate.ui.theme.rememberMotionActive
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun buildDitherNoiseShaderBrush(): ShaderBrush {
    val size = 64
    val pixels = IntArray(size * size)
    val rng = Random(7331)
    for (i in pixels.indices) {
        val bright = rng.nextBoolean()
        val c = if (bright) 255 else 12
        val a = rng.nextInt(8, 22)
        pixels[i] = (a shl 24) or (c shl 16) or (c shl 8) or c
    }
    val bmp = android.graphics.Bitmap.createBitmap(
        pixels,
        size,
        size,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    return ShaderBrush(
        ImageShader(
            image = bmp.asImageBitmap(),
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated
        )
    )
}

/**
 * Zero-recomposition adaptive ambient backdrop:
 * - Liquid Glass: Luminous iridescent caustics & dispersion mesh
 * - Material 3 Expressive: High-chroma tonal washes & soft concentric halos
 * - Material You Lite: Lightweight clean tonal scrim with minimal GPU overdraw
 * - Cyber Slate / Morph: Deep blurred ambient glow layers with breathing, shift, & micro-dither
 */
@Composable
fun FrostedBackdrop(
    isFirewallActive: Boolean,
    backgroundAnimation: BackgroundAnimationStyle = BackgroundAnimationStyle.NONE,
    backgroundParallaxEnabled: Boolean = false,
    backgroundSeasonalEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val themeStyle = LocalThemeStyle.current
    val useDynamicColor = LocalDynamicColorEnabled.current
    val baseColor = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val primaryGlowState = animateColorAsState(
        targetValue = if (isFirewallActive) primary else secondary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "backdropPrimaryGlow"
    )

    val motionActive by rememberMotionActive(blurConfig.animateMotion)
    val shouldAnimateDrift = motionActive &&
        blurConfig.style != BlurEngineStyle.OFF &&
        themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE

    val driftProgressState = rememberLifecycleMotionProgress(
        active = shouldAnimateDrift,
        durationMillis = when {
            blurConfig.style == BlurEngineStyle.MORPH -> 13_000
            themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 14_000
            else -> 18_000
        },
        initialPhase = 0.14f,
        label = "cyberAuroraDrift"
    )

    val intensityScale = blurConfig.intensity.coerceIn(0.4f, 1.5f)
    val baseIntensityAlpha = when (themeStyle) {
        NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.10f
        NetGateThemeStyle.LIQUID_GLASS -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.16f
            BlurEngineStyle.SOFT_GLASS -> 0.28f
            BlurEngineStyle.ACRYLIC_PRO -> 0.36f
            BlurEngineStyle.ULTRA_FROST -> 0.44f
            BlurEngineStyle.MORPH -> 0.50f
        }
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.14f
            BlurEngineStyle.SOFT_GLASS -> 0.26f
            BlurEngineStyle.ACRYLIC_PRO -> 0.34f
            BlurEngineStyle.ULTRA_FROST -> 0.40f
            BlurEngineStyle.MORPH -> 0.48f
        }
        NetGateThemeStyle.NETGATE_DEFAULT -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.12f
            BlurEngineStyle.SOFT_GLASS -> 0.22f
            BlurEngineStyle.ACRYLIC_PRO -> 0.30f
            BlurEngineStyle.ULTRA_FROST -> 0.38f
            BlurEngineStyle.MORPH -> 0.46f
        }
    }
    val intensityAlpha = (baseIntensityAlpha * (0.70f + 0.30f * intensityScale)).coerceIn(0.08f, 0.65f)

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
                        progressState = driftProgressState,
                        primaryGlowState = primaryGlowState,
                        secondary = secondary,
                        tertiary = tertiary,
                        intensityAlpha = intensityAlpha,
                        intensityScale = intensityScale,
                        blurStyle = blurConfig.style,
                        isDark = isDark,
                        showCyberRings = isFirewallActive && blurConfig.style != BlurEngineStyle.OFF,
                        isLiquidGlass = themeStyle == NetGateThemeStyle.LIQUID_GLASS,
                        useDynamicColor = useDynamicColor,
                        motionActive = shouldAnimateDrift
                    )
                }
            }
        }

        AnimatedAppBackground(
            style = backgroundAnimation,
            parallaxEnabled = backgroundParallaxEnabled,
            seasonalEnabled = backgroundSeasonalEnabled,
            modifier = Modifier.matchParentSize()
        )

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
    progressState: State<Float>,
    primaryGlowState: State<Color>,
    secondary: Color,
    tertiary: Color,
    intensityAlpha: Float,
    intensityScale: Float,
    blurStyle: BlurEngineStyle,
    isDark: Boolean,
    showCyberRings: Boolean,
    isLiquidGlass: Boolean,
    useDynamicColor: Boolean,
    motionActive: Boolean
) {
    val isMorph = blurStyle == BlurEngineStyle.MORPH
    val ditherBrush = remember { buildDitherNoiseShaderBrush() }

    val baseBlurDp = when (blurStyle) {
        BlurEngineStyle.OFF -> 0f
        BlurEngineStyle.SOFT_GLASS -> 6f
        BlurEngineStyle.ACRYLIC_PRO -> 12f
        BlurEngineStyle.ULTRA_FROST -> 18f
        BlurEngineStyle.MORPH -> 24f
    } * intensityScale

    // Morph uses a deeper teal/blue + accent wash layered smoothly under an offscreen blurred canvas
    val morphTeal = if (useDynamicColor) secondary else LiquidGlassAqua
    val morphBlue = if (useDynamicColor) tertiary else LiquidGlassCyan

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .blurEngineBackdrop(
                    radius = baseBlurDp.dp,
                    saturation = blurStyle.saturationBoost * (0.92f + 0.08f * intensityScale)
                )
        ) {
            val phase = (progressState.value * 2.0 * PI)
            val primaryGlow = primaryGlowState.value

            val driftAmp = when {
                isMorph -> 0.125f
                isLiquidGlass -> 0.095f
                else -> 0.07f
            }
            val driftX = sin(phase).toFloat() * size.width * driftAmp
            val driftY = cos(phase * 0.85).toFloat() * size.height * (driftAmp * 0.9f)
            val breath = 1f + (if (isMorph) 0.12f else 0.07f) * sin(phase * 1.5).toFloat()
            val scaleBoost = if (isDark) 1.0f else 0.80f

            if (isMorph) {
                // Rotating/shifting ambient gradient wash characteristic of the Morph background
                val angleCos = cos(phase * 0.5).toFloat()
                val angleSin = sin(phase * 0.5).toFloat()
                val sweepStart = Offset(
                    x = size.width * (0.5f - 0.5f * angleCos),
                    y = size.height * (0.5f - 0.5f * angleSin)
                )
                val sweepEnd = Offset(
                    x = size.width * (0.5f + 0.5f * angleCos),
                    y = size.height * (0.5f + 0.5f * angleSin)
                )
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            morphTeal.copy(alpha = (if (isDark) 0.22f else 0.16f) * intensityScale),
                            primaryGlow.copy(alpha = (if (isDark) 0.14f else 0.10f) * intensityScale),
                            morphBlue.copy(alpha = (if (isDark) 0.20f else 0.14f) * intensityScale)
                        ),
                        start = sweepStart,
                        end = sweepEnd
                    )
                )
            }

            val topLeftCenter = Offset(
                x = size.width * 0.18f + driftX,
                y = size.height * 0.14f + driftY
            )
            val topLeftRadius = size.maxDimension * (if (isMorph) 0.72f else 0.62f) * breath
            val topLeftAlpha = (intensityAlpha * scaleBoost).coerceAtMost(0.75f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to (if (isMorph) morphTeal else primaryGlow).copy(alpha = topLeftAlpha),
                        0.45f to primaryGlow.copy(alpha = topLeftAlpha * 0.44f),
                        0.82f to primaryGlow.copy(alpha = topLeftAlpha * 0.09f),
                        1.0f to Color.Transparent
                    ),
                    center = topLeftCenter,
                    radius = topLeftRadius
                ),
                radius = topLeftRadius,
                center = topLeftCenter
            )

            val rightCenter = Offset(
                x = size.width * 0.86f - driftX * 0.9f,
                y = size.height * 0.38f + driftY * 1.1f
            )
            val rightRadius = size.maxDimension * (if (isMorph) 0.64f else 0.54f) * (2f - breath)
            val rightAlpha = (intensityAlpha * 0.86f * scaleBoost).coerceAtMost(0.70f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to (if (isMorph) morphBlue else secondary).copy(alpha = rightAlpha),
                        0.48f to secondary.copy(alpha = rightAlpha * 0.40f),
                        0.85f to secondary.copy(alpha = rightAlpha * 0.08f),
                        1.0f to Color.Transparent
                    ),
                    center = rightCenter,
                    radius = rightRadius
                ),
                radius = rightRadius,
                center = rightCenter
            )

            val bottomCenter = Offset(
                x = size.width * 0.30f - driftY * 0.85f,
                y = size.height * 0.86f - driftX * 0.85f
            )
            val bottomRadius = size.maxDimension * (if (isMorph) 0.68f else 0.58f) * breath
            val bottomAlpha = (intensityAlpha * 0.88f * scaleBoost).coerceAtMost(0.70f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to tertiary.copy(alpha = bottomAlpha),
                        0.46f to primaryGlow.copy(alpha = bottomAlpha * 0.42f),
                        0.84f to tertiary.copy(alpha = bottomAlpha * 0.08f),
                        1.0f to Color.Transparent
                    ),
                    center = bottomCenter,
                    radius = bottomRadius
                ),
                radius = bottomRadius,
                center = bottomCenter
            )

            if (showCyberRings) {
                val ringCenter = Offset(
                    x = size.width * 0.84f - driftX * 0.2f,
                    y = size.height * 0.12f + driftY * 0.2f
                )
                val pulse = ((sin(phase * 2.0) + 1.0) * 0.5).toFloat()
                val ringRadius = size.minDimension * (0.20f + 0.08f * pulse)
                drawCircle(
                    color = primaryGlow.copy(alpha = (0.10f - 0.05f * pulse).coerceAtLeast(0.015f)),
                    radius = ringRadius,
                    center = ringCenter,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawCircle(
                    color = secondary.copy(alpha = 0.05f),
                    radius = size.minDimension * 0.34f,
                    center = ringCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Anti-banding dither grain overlay with subtle ambient motion when Morph / frosted active
        if (blurStyle != BlurEngineStyle.OFF) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                val p = progressState.value.toDouble() * 2.0 * PI
                val jitterX = if (motionActive) sin(p * 3.0).toFloat() * 6f else 0f
                val jitterY = if (motionActive) cos(p * 2.0).toFloat() * 6f else 0f
                translate(jitterX, jitterY) {
                    drawRect(
                        brush = ditherBrush,
                        alpha = if (isMorph) 0.42f else 0.24f
                    )
                }
            }
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
    val useDynamicColor = LocalDynamicColorEnabled.current
    val activeLiquidState = liquidStateOverride ?: LocalBackdropLiquidState.current
    val useFletchLiquid = themeStyle == NetGateThemeStyle.LIQUID_GLASS && activeLiquidState != null
    val isMorph = blurConfig.style == BlurEngineStyle.MORPH
    val intensityScale = blurConfig.intensity.coerceIn(0.4f, 1.5f)

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
                (blurConfig.style.tintAlphaDark + 0.04f).coerceIn(0.46f, 0.86f)
            } else {
                (blurConfig.style.tintAlphaLight + 0.04f).coerceIn(0.58f, 0.92f)
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
        NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> if (isMorph) 0.85f else 0.55f
        NetGateThemeStyle.LIQUID_GLASS -> 1.35f
        NetGateThemeStyle.NETGATE_DEFAULT -> when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0.25f
            BlurEngineStyle.SOFT_GLASS -> 0.75f
            BlurEngineStyle.ACRYLIC_PRO -> 1.0f
            BlurEngineStyle.ULTRA_FROST -> 1.25f
            BlurEngineStyle.MORPH -> 1.32f
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val morphAccentPrimary = MaterialTheme.colorScheme.primary
    val morphAccentSecondary = if (useDynamicColor) MaterialTheme.colorScheme.secondary else LiquidGlassCyan
    val borderBrush = remember(isDark, borderAccent, sheenStrength, outlineColor, themeStyle, isMorph, morphAccentPrimary, morphAccentSecondary) {
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
            isMorph -> {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.34f else 0.88f),
                        morphAccentSecondary.copy(alpha = if (isDark) 0.22f else 0.32f),
                        morphAccentPrimary.copy(alpha = if (isDark) 0.16f else 0.24f),
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.36f)
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

    val sheenBrush = remember(isDark, sheenStrength, themeStyle, isMorph) {
        if (themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE && !isMorph) {
            Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = (0.14f * sheenStrength).coerceIn(0.03f, 0.24f)),
                        Color.White.copy(alpha = 0.025f),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = (0.54f * sheenStrength).coerceIn(0.18f, 0.80f)),
                        Color.White.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                }
            )
        }
    }

    val glintModifier = if (motionSpec.enableCornerGlints || isMorph) {
        Modifier.drawWithContent {
            drawContent()
            val glintAlpha = if (isDark) 0.40f else 0.72f
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
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    } else {
        Modifier
    }

    val cardFrostDp = blurConfig.style.liquidFrostDp(intensityScale)
    val enableOpticalFrostLayer = blurConfig.style != BlurEngineStyle.OFF &&
        themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val ditherBrush = remember { buildDitherNoiseShaderBrush() }

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
                        this.saturation = blurConfig.style.saturationBoost
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
            width = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS || isMorph) 1.15.dp else 1.dp,
            brush = borderBrush
        )
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (motionSpec.enableRefractionShader) {
                            Modifier.liquidGlassRefractionEdge(
                                enabled = true,
                                shape = shape
                            )
                        } else {
                            Modifier.frostedChromaticEdge(
                                shape = shape,
                                borderAccent = borderAccent
                            )
                        }
                    )
                    .background(sheenBrush)
                    .then(glintModifier)
            ) {
                if (enableOpticalFrostLayer && !useFletchLiquid) {
                    val glowStrength = when (blurConfig.style) {
                        BlurEngineStyle.OFF -> 0f
                        BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.07f else 0.05f
                        BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.11f else 0.08f
                        BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.16f else 0.11f
                        BlurEngineStyle.MORPH -> if (isDark) 0.22f else 0.14f
                    } * intensityScale
                    val blurRadius = (blurConfig.style.modalBlurDp * 1.15f * intensityScale).dp
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .blurEngineBackdrop(
                                radius = blurRadius,
                                saturation = blurConfig.style.saturationBoost
                            )
                            .drawBehind {
                                val topSpot = Offset(size.width * 0.14f, size.height * 0.08f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            (borderAccent ?: primaryColor).copy(alpha = glowStrength.coerceAtMost(0.45f)),
                                            Color.Transparent
                                        ),
                                        center = topSpot,
                                        radius = size.maxDimension * 0.58f
                                    ),
                                    center = topSpot,
                                    radius = size.maxDimension * 0.58f
                                )
                                val bottomSpot = Offset(size.width * 0.90f, size.height * 0.92f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            secondaryColor.copy(alpha = (glowStrength * 0.88f).coerceAtMost(0.40f)),
                                            tertiaryColor.copy(alpha = (glowStrength * 0.45f).coerceAtMost(0.25f)),
                                            Color.Transparent
                                        ),
                                        center = bottomSpot,
                                        radius = size.maxDimension * 0.52f
                                    ),
                                    center = bottomSpot,
                                    radius = size.maxDimension * 0.52f
                                )
                                if (isMorph) {
                                    drawRect(brush = ditherBrush, alpha = 0.28f)
                                }
                            }
                    )
                }
                content()
            }
        }
    }
}

/**
 * Provides a frosted/liquid/Morph glass ambient aurora backdrop & [LocalBackdropLiquidState]
 * inside ModalBottomSheets so cards and controls over sheets share the exact app glass style.
 */
@Composable
fun FrostedSheetContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val themeStyle = LocalThemeStyle.current
    val blurConfig = LocalBlurEngineConfig.current
    val isDark = LocalIsDarkTheme.current
    val useDynamicColor = LocalDynamicColorEnabled.current
    val sheetSurfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val intensityScale = blurConfig.intensity.coerceIn(0.4f, 1.5f)
    val isMorph = blurConfig.style == BlurEngineStyle.MORPH

    val sheetLiquidState = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS &&
        blurConfig.style != BlurEngineStyle.OFF
    ) {
        rememberLiquidState()
    } else {
        null
    }

    val motionActive by rememberMotionActive(blurConfig.animateMotion)
    val sheetMotionActive = motionActive &&
        blurConfig.style != BlurEngineStyle.OFF &&
        themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE
    val progressState = rememberLifecycleMotionProgress(
        active = sheetMotionActive,
        durationMillis = if (isMorph) 14_000 else 18_000,
        initialPhase = 0.22f,
        label = "frostedSheetDrift"
    )

    val showMesh = themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE &&
        blurConfig.style != BlurEngineStyle.OFF
    val ditherBrush = remember { buildDitherNoiseShaderBrush() }

    val baseSheetAlpha = when (blurConfig.style) {
        BlurEngineStyle.OFF -> if (isDark) 0.98f else 0.99f
        BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.95f else 0.97f
        BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.94f else 0.96f
        BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.93f else 0.95f
        BlurEngineStyle.MORPH -> if (isDark) 0.92f else 0.95f
    }

    val sheetTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(sheetTopShape)
            .background(sheetSurfaceColor.copy(alpha = baseSheetAlpha))
    ) {
        if (showMesh) {
            val meshAlpha = when (blurConfig.style) {
                BlurEngineStyle.OFF -> 0f
                BlurEngineStyle.SOFT_GLASS -> if (isDark) 0.18f else 0.14f
                BlurEngineStyle.ACRYLIC_PRO -> if (isDark) 0.24f else 0.18f
                BlurEngineStyle.ULTRA_FROST -> if (isDark) 0.30f else 0.24f
                BlurEngineStyle.MORPH -> if (isDark) 0.36f else 0.28f
            } * intensityScale.coerceAtMost(1.35f)
            val c1 = when {
                isMorph && !useDynamicColor -> LiquidGlassAqua
                themeStyle == NetGateThemeStyle.LIQUID_GLASS -> LiquidGlassCyan
                else -> primaryColor
            }
            val c2 = when {
                isMorph && !useDynamicColor -> LiquidGlassCyan
                themeStyle == NetGateThemeStyle.LIQUID_GLASS -> LiquidGlassAqua
                else -> secondaryColor
            }
            val c3 = if (themeStyle == NetGateThemeStyle.LIQUID_GLASS) LiquidGlassPrismViolet else tertiaryColor

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .then(
                        if (sheetLiquidState != null) {
                            Modifier.liquefiable(sheetLiquidState)
                        } else {
                            Modifier
                        }
                    )
                    .blurEngineBackdrop(
                        radius = (blurConfig.style.modalBlurDp * 1.45f * intensityScale).dp,
                        saturation = blurConfig.style.saturationBoost
                    )
            ) {
                val p = progressState.value * 2.0 * PI
                val dx = sin(p).toFloat() * size.width * 0.08f
                val dy = cos(p * 0.85).toFloat() * size.height * 0.06f

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            sheetSurfaceColor.copy(alpha = (baseSheetAlpha * 0.85f).coerceIn(0.45f, 0.92f)),
                            sheetSurfaceColor.copy(alpha = (baseSheetAlpha * 0.96f).coerceIn(0.55f, 0.96f))
                        )
                    )
                )
                val center1 = Offset(size.width * 0.16f + dx, size.height * 0.16f + dy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c1.copy(alpha = meshAlpha.coerceAtMost(0.55f)), Color.Transparent),
                        center = center1,
                        radius = size.maxDimension * 0.62f
                    ),
                    center = center1,
                    radius = size.maxDimension * 0.62f
                )
                val center2 = Offset(size.width * 0.88f - dx, size.height * 0.44f + dy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c2.copy(alpha = (meshAlpha * 0.90f).coerceAtMost(0.50f)), Color.Transparent),
                        center = center2,
                        radius = size.maxDimension * 0.54f
                    ),
                    center = center2,
                    radius = size.maxDimension * 0.54f
                )
                val center3 = Offset(size.width * 0.28f - dy, size.height * 0.82f - dx)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c3.copy(alpha = (meshAlpha * 0.82f).coerceAtMost(0.45f)), Color.Transparent),
                        center = center3,
                        radius = size.maxDimension * 0.56f
                    ),
                    center = center3,
                    radius = size.maxDimension * 0.56f
                )
                if (isMorph) {
                    drawRect(brush = ditherBrush, alpha = 0.26f)
                }
            }
        }

        // Subtle glass rim highlight along sheet top curve
        Box(
            modifier = Modifier
                .matchParentSize()
                .frostedChromaticEdge(shape = sheetTopShape)
        )

        CompositionLocalProvider(
            LocalBackdropLiquidState provides sheetLiquidState,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            content()
        }
    }
}

@Composable
fun FrostedSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        )
    }
}
