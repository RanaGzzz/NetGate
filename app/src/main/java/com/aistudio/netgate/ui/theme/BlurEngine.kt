package com.aistudio.netgate.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.BlurEngineStyle
import com.aistudio.netgate.data.model.NetGateThemeStyle
import io.github.fletchmckee.liquid.LiquidState
import kotlin.math.roundToInt

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class BlurEngineConfig(
    val style: BlurEngineStyle = BlurEngineStyle.ACRYLIC_PRO,
    val intensity: Float = 1.0f,
    val animateMotion: Boolean = true,
    val themeStyle: NetGateThemeStyle = NetGateThemeStyle.NETGATE_DEFAULT,
    val useDynamicColor: Boolean = false
)

data class ExpressiveMotionSpec(
    val themeStyle: NetGateThemeStyle = NetGateThemeStyle.NETGATE_DEFAULT,
    val spatialDampingRatio: Float = themeStyle.springDampingRatio,
    val spatialStiffness: Float = themeStyle.springStiffness,
    val enableRefractionShader: Boolean = themeStyle == NetGateThemeStyle.LIQUID_GLASS,
    val enablePrismEdges: Boolean = themeStyle == NetGateThemeStyle.LIQUID_GLASS,
    val enableCornerGlints: Boolean = themeStyle == NetGateThemeStyle.LIQUID_GLASS,
    val enableLiquidTouchPhysics: Boolean =
        themeStyle == NetGateThemeStyle.LIQUID_GLASS ||
            themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE
)

val LocalBlurEngineConfig = staticCompositionLocalOf { BlurEngineConfig() }
val LocalExpressiveMotion = staticCompositionLocalOf { ExpressiveMotionSpec() }
val LocalBackdropLiquidState = staticCompositionLocalOf<LiquidState?> { null }
val LocalOverlayLiquidState = staticCompositionLocalOf<LiquidState?> { null }
val LocalPerformanceMode = staticCompositionLocalOf { false }

@Immutable
data class GlassBlurStyle(
    val blurStyle: BlurEngineStyle,
    val themeStyle: NetGateThemeStyle,
    val isDark: Boolean,
    val blurSupported: Boolean,
    val isBlurActive: Boolean,
    val isReducedMotion: Boolean,
    val isPerformanceFallback: Boolean,
    val useFletchLiquid: Boolean,
    val liquidState: LiquidState?,
    val containerBlurDp: Dp,
    val navBlurDp: Dp,
    val liquidFrostDp: Dp,
    val saturation: Float,
    val containerColor: Color,
    val containerBorderColor: Color,
    val containerBorderWidth: Dp,
    val menuColor: Color,
    val menuBorderColor: Color,
    val indicatorColor: Color,
    val activeContentColor: Color,
    val inactiveContentColor: Color,
    val activeBadgeBgColor: Color,
    val activeBadgeTextColor: Color,
    val inactiveBadgeBgColor: Color,
    val inactiveBadgeTextColor: Color,
    val accentColor: Color,
    val styleTransitionMillis: Int,
    val indicatorTransitionMillis: Int,
    val liquidRefraction: Float,
    val liquidCurve: Float,
    val liquidEdge: Float,
    val liquidDispersion: Float
)

fun BlurEngineStyle.liquidFrostDp(intensity: Float = 1f): Dp = when (this) {
    BlurEngineStyle.OFF -> 0.dp
    BlurEngineStyle.SOFT_GLASS -> (8f * intensity).dp
    BlurEngineStyle.ACRYLIC_PRO -> (14f * intensity).dp
    BlurEngineStyle.ULTRA_FROST -> (20f * intensity).dp
    BlurEngineStyle.MORPH -> (24f * intensity).dp
}

fun BlurEngineStyle.navLiquidFrostDp(intensity: Float = 1f): Dp = when (this) {
    BlurEngineStyle.OFF -> 0.dp
    BlurEngineStyle.SOFT_GLASS -> (6f * intensity).dp
    BlurEngineStyle.ACRYLIC_PRO -> (10f * intensity).dp
    BlurEngineStyle.ULTRA_FROST -> (15f * intensity).dp
    BlurEngineStyle.MORPH -> (18f * intensity).dp
}

/**
 * Observes both Lifecycle resumed state and Android's "Remove animations" accessibility setting
 * (`ANIMATOR_DURATION_SCALE == 0f`) so ambient background & glass animations pause when backgrounded
 * and fall back to a static composition when animations are disabled.
 */
@Composable
fun rememberMotionActive(animateMotion: Boolean): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialReduced = remember(context) { isSystemRemoveAnimationsEnabled(context) }

    // Lifecycle.currentState must not be read during composition: doing so never observes later
    // changes. The DisposableEffect below seeds the real value, and it runs before the first frame.
    val isResumed = remember { mutableStateOf(false) }
    val isReducedMotion = remember { mutableStateOf(initialReduced) }
    val activeState = remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, animateMotion) {
        isResumed.value = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        activeState.value = animateMotion && isResumed.value && !isReducedMotion.value
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed.value = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isResumed.value = false
                else -> Unit
            }
            activeState.value = animateMotion && isResumed.value && !isReducedMotion.value
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, animateMotion) {
        val resolver = context.contentResolver
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isReducedMotion.value = isSystemRemoveAnimationsEnabled(context)
                activeState.value = animateMotion && isResumed.value && !isReducedMotion.value
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                contentObserver
            )
        }
        onDispose {
            runCatching { resolver.unregisterContentObserver(contentObserver) }
        }
    }

    return activeState
}

fun isSystemRemoveAnimationsEnabled(context: android.content.Context): Boolean {
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)
}

fun isSystemReduceTransparencyEnabled(context: android.content.Context): Boolean {
    return runCatching {
        Settings.Secure.getInt(context.contentResolver, "reduce_transparency", 0) == 1 ||
            Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
    }.getOrDefault(false)
}

fun wcagContrastRatio(foreground: Color, background: Color): Float {
    val fg = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
    val l1 = fg.luminance()
    val l2 = background.luminance()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

fun resolveWcagAccessibleContentColor(
    background: Color,
    preferred: Color,
    secondaryFallback: Color? = null,
    minRatio: Float = 4.5f
): Color {
    if (wcagContrastRatio(preferred, background) >= minRatio) {
        return preferred
    }
    if (secondaryFallback != null && wcagContrastRatio(secondaryFallback, background) >= minRatio) {
        return secondaryFallback
    }
    val darkInk = Color(0xFF0A1018)
    val lightInk = Color(0xFFF8FAFC)
    val darkRatio = wcagContrastRatio(darkInk, background)
    val lightRatio = wcagContrastRatio(lightInk, background)
    return if (lightRatio >= darkRatio) lightInk else darkInk
}

@Composable
fun useBlurStyle(
    accentColor: Color = MaterialTheme.colorScheme.primary,
    useOverlayLiquid: Boolean = false
): GlassBlurStyle {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val config = LocalBlurEngineConfig.current
    val themeStyle = LocalThemeStyle.current
    val isPerformanceMode = LocalPerformanceMode.current
    val backdropLiquid = LocalBackdropLiquidState.current
    val overlayLiquid = LocalOverlayLiquidState.current
    val resolvedLiquidState = if (useOverlayLiquid) overlayLiquid ?: backdropLiquid else backdropLiquid

    val isReducedMotion = remember(context, config.animateMotion) {
        !config.animateMotion || isSystemRemoveAnimationsEnabled(context)
    }
    val reduceTransparency = remember(context) {
        isSystemReduceTransparencyEnabled(context)
    }

    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isPerformanceFallback = !blurSupported ||
        isPerformanceMode ||
        reduceTransparency ||
        config.style == BlurEngineStyle.OFF
    val isBlurActive = !isPerformanceFallback &&
        themeStyle != NetGateThemeStyle.MATERIAL_YOU_LITE

    val useFletchLiquid = isBlurActive &&
        themeStyle == NetGateThemeStyle.LIQUID_GLASS &&
        resolvedLiquidState != null

    val intensityScale = config.intensity.coerceIn(0.4f, 1.5f)
    val styleTransitionMillis = if (isReducedMotion) 0 else 180
    val indicatorTransitionMillis = if (isReducedMotion) 0 else 200

    val basePresetAlpha = if (isDark) config.style.tintAlphaDark else config.style.tintAlphaLight
    val fallbackPresetAlpha = if (reduceTransparency) {
        0.96f
    } else if (isDark) {
        BlurEngineStyle.OFF.tintAlphaDark
    } else {
        BlurEngineStyle.OFF.tintAlphaLight
    }

    val targetContainerAlpha = when {
        !isBlurActive -> fallbackPresetAlpha
        useFletchLiquid -> (basePresetAlpha * 0.78f).coerceIn(0.30f, 0.78f)
        else -> basePresetAlpha
    }

    val targetContainerBlur = if (isBlurActive) {
        (config.style.modalBlurDp * intensityScale).dp
    } else {
        0.dp
    }
    val targetNavBlur = if (isBlurActive) {
        (config.style.navLiquidFrostDp(intensityScale).value * 1.15f).dp
    } else {
        0.dp
    }
    val targetLiquidFrost = if (useFletchLiquid) {
        if (useOverlayLiquid) config.style.navLiquidFrostDp(intensityScale) else config.style.liquidFrostDp(intensityScale)
    } else {
        0.dp
    }

    val colorScheme = MaterialTheme.colorScheme
    val tintWeight = ((config.style.saturationBoost - 1.0f) * 0.45f).coerceIn(0.04f, 0.16f)
    val baseContainerSurface = if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow
    val targetContainerBase = lerp(baseContainerSurface, colorScheme.primaryContainer, tintWeight)
    val targetContainerColor = targetContainerBase.copy(alpha = targetContainerAlpha)

    val menuAlpha = if (isBlurActive) {
        (basePresetAlpha + 0.24f).coerceIn(0.78f, 0.96f)
    } else {
        fallbackPresetAlpha.coerceAtLeast(0.94f)
    }
    val targetMenuColor = lerp(
        if (isDark) colorScheme.surfaceContainer else colorScheme.surface,
        colorScheme.primaryContainer,
        tintWeight * 0.75f
    ).copy(alpha = menuAlpha)

    val borderStrength = when {
        reduceTransparency -> 0.50f
        !isBlurActive -> 0.34f
        else -> (0.20f + (1f - basePresetAlpha) * 0.35f).coerceIn(0.18f, 0.42f)
    }
    val targetBorderBase = if (isDark) {
        lerp(colorScheme.outlineVariant, Color.White, 0.18f)
    } else {
        lerp(colorScheme.outline, Color.White, 0.28f)
    }
    val targetContainerBorderColor = targetBorderBase.copy(alpha = borderStrength)
    val targetMenuBorderColor = targetBorderBase.copy(alpha = (borderStrength * 1.15f).coerceAtMost(0.55f))

    // Active indicator derives directly from the user's Material You / theme primary accent + preset alpha
    val indicatorTint = if (accentColor.luminance() > 0.56f && !isDark) {
        lerp(accentColor, colorScheme.primaryContainer, 0.18f)
    } else {
        accentColor
    }
    val indicatorAlpha = if (isBlurActive) {
        (basePresetAlpha + 0.36f).coerceIn(0.88f, 0.98f)
    } else {
        0.96f
    }
    val targetIndicatorColor = indicatorTint.copy(alpha = indicatorAlpha)

    // Composite colors against the screen background to compute true WCAG >= 4.5:1 contrast
    val effectiveContainerBg = targetContainerColor.compositeOver(colorScheme.background)
    val effectiveIndicatorBg = targetIndicatorColor.compositeOver(effectiveContainerBg)

    val targetActiveContentColor = resolveWcagAccessibleContentColor(
        background = effectiveIndicatorBg,
        preferred = colorScheme.onPrimary,
        secondaryFallback = colorScheme.onPrimaryContainer,
        minRatio = 4.5f
    )
    val targetInactiveContentColor = resolveWcagAccessibleContentColor(
        background = effectiveContainerBg,
        preferred = colorScheme.onSurfaceVariant,
        secondaryFallback = colorScheme.onSurface,
        minRatio = 4.5f
    )

    val crossfadeColorSpec = tween<Color>(
        durationMillis = styleTransitionMillis,
        easing = FastOutSlowInEasing
    )
    val crossfadeDpSpec = tween<Dp>(
        durationMillis = styleTransitionMillis,
        easing = FastOutSlowInEasing
    )

    val containerBlurDp by animateDpAsState(targetContainerBlur, crossfadeDpSpec, label = "blurStyleContainerBlur")
    val navBlurDp by animateDpAsState(targetNavBlur, crossfadeDpSpec, label = "blurStyleNavBlur")
    val liquidFrostDp by animateDpAsState(targetLiquidFrost, crossfadeDpSpec, label = "blurStyleLiquidFrost")
    val containerColor by animateColorAsState(targetContainerColor, crossfadeColorSpec, label = "blurStyleContainerBg")
    val containerBorderColor by animateColorAsState(targetContainerBorderColor, crossfadeColorSpec, label = "blurStyleContainerBorder")
    val menuColor by animateColorAsState(targetMenuColor, crossfadeColorSpec, label = "blurStyleMenuBg")
    val menuBorderColor by animateColorAsState(targetMenuBorderColor, crossfadeColorSpec, label = "blurStyleMenuBorder")
    val indicatorColor by animateColorAsState(targetIndicatorColor, crossfadeColorSpec, label = "blurStyleIndicatorBg")
    val activeContentColor by animateColorAsState(targetActiveContentColor, crossfadeColorSpec, label = "blurStyleActiveFg")
    val inactiveContentColor by animateColorAsState(targetInactiveContentColor, crossfadeColorSpec, label = "blurStyleInactiveFg")

    val saturation by animateFloatAsState(
        targetValue = if (isBlurActive) config.style.saturationBoost else 1f,
        animationSpec = if (isReducedMotion) snap() else tween(styleTransitionMillis, easing = FastOutSlowInEasing),
        label = "blurStyleSaturation"
    )

    return GlassBlurStyle(
        blurStyle = config.style,
        themeStyle = themeStyle,
        isDark = isDark,
        blurSupported = blurSupported,
        isBlurActive = isBlurActive,
        isReducedMotion = isReducedMotion,
        isPerformanceFallback = isPerformanceFallback,
        useFletchLiquid = useFletchLiquid,
        liquidState = resolvedLiquidState,
        containerBlurDp = containerBlurDp,
        navBlurDp = navBlurDp,
        liquidFrostDp = liquidFrostDp,
        saturation = saturation,
        containerColor = containerColor,
        containerBorderColor = containerBorderColor,
        containerBorderWidth = 1.dp,
        menuColor = menuColor,
        menuBorderColor = menuBorderColor,
        indicatorColor = indicatorColor,
        activeContentColor = activeContentColor,
        inactiveContentColor = inactiveContentColor,
        activeBadgeBgColor = activeContentColor.copy(alpha = 0.20f),
        activeBadgeTextColor = activeContentColor,
        inactiveBadgeBgColor = accentColor.copy(alpha = 0.16f),
        inactiveBadgeTextColor = resolveWcagAccessibleContentColor(
            background = accentColor.copy(alpha = 0.16f).compositeOver(effectiveContainerBg),
            preferred = accentColor,
            secondaryFallback = targetInactiveContentColor,
            minRatio = 4.5f
        ),
        accentColor = accentColor,
        styleTransitionMillis = styleTransitionMillis,
        indicatorTransitionMillis = indicatorTransitionMillis,
        liquidRefraction = if (useOverlayLiquid) 0.26f else 0.20f,
        liquidCurve = if (useOverlayLiquid) 0.30f else 0.24f,
        liquidEdge = if (useOverlayLiquid) 0.06f else 0.04f,
        liquidDispersion = if (useOverlayLiquid) 0.03f else 0.02f
    )
}

/**
 * Provides a normalized 0f..1f cyclic progress [State] that:
 * - Instantiates `rememberInfiniteTransition` unconditionally (safe under Compose test clocks).
 * - Pauses in place when [active] becomes false (backgrounded or "Remove animations" enabled),
 *   stopping frame reads so Canvas/DrawScope incurs zero background CPU/GPU drain.
 * - Resumes smoothly from the exact paused phase without jumping when [active] returns true.
 */
@Composable
fun rememberLifecycleMotionProgress(
    active: Boolean,
    durationMillis: Int = 18_000,
    initialPhase: Float = 0.14f,
    label: String = "lifecycleMotion"
): State<Float> {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = label)
    val runningPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = durationMillis,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "${label}_phase"
    )
    val frozenPhase = remember { androidx.compose.runtime.mutableFloatStateOf(initialPhase) }
    val phaseShift = remember { androidx.compose.runtime.mutableFloatStateOf(initialPhase) }
    val wasActive = remember { androidx.compose.runtime.mutableStateOf(active) }

    androidx.compose.runtime.LaunchedEffect(active) {
        if (active && !wasActive.value) {
            phaseShift.floatValue = (frozenPhase.floatValue - runningPhase.value + 2f) % 1f
        } else if (!active && wasActive.value) {
            frozenPhase.floatValue = (runningPhase.value + phaseShift.floatValue) % 1f
        }
        wasActive.value = active
    }

    return remember(active) {
        androidx.compose.runtime.derivedStateOf {
            if (active) {
                val p = (runningPhase.value + phaseShift.floatValue) % 1f
                frozenPhase.floatValue = p
                p
            } else {
                frozenPhase.floatValue
            }
        }
    }
}

/**
 * Hardware-accelerated RenderEffect + spring-driven blur/tint & Liquid Glass optical rim compositor.
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
        val isLite = config.themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE
        val targetBlur = if (active && config.style != BlurEngineStyle.OFF && !isLite) {
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

    /**
     * Applies a strictly shape-clipped optical glass meniscus bevel & subtle chromatic dispersion
     * inner-rim highlight when Liquid Glass mode is active. Keeps foreground text and controls
     * razor-sharp inside scrolled containers and bottom sheets without HWUI RenderEffect coordinate smearing.
     */
    fun Modifier.liquidGlassRefractionEdge(
        enabled: Boolean = true,
        shape: Shape = RoundedCornerShape(20.dp),
        strength: Float = 0.022f
    ): Modifier = composed {
        val motion = LocalExpressiveMotion.current
        if (!enabled || !motion.enableRefractionShader) {
            return@composed this
        }
        val isDark = LocalIsDarkTheme.current
        val intensity = (strength * 30f).coerceIn(0.40f, 1.0f)

        this
            .clip(shape)
            .drawWithContent {
                drawContent()
                val bandHeight = (size.height * 0.34f).coerceIn(10.dp.toPx(), 42.dp.toPx())
                // Top curved liquid meniscus reflection
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (if (isDark) 0.11f else 0.32f) * intensity),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = bandHeight
                    )
                )
                // Subtle bottom aqua-violet internal dispersion bounce
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            LiquidGlassCyan.copy(alpha = (if (isDark) 0.07f else 0.12f) * intensity)
                        ),
                        startY = (size.height - bandHeight).coerceAtLeast(0f),
                        endY = size.height
                    )
                )
                // Crisp inner chromatic glass bevel aligned strictly to the exact Shape outline
                val outline = shape.createOutline(
                    size = Size(size.width, size.height),
                    layoutDirection = layoutDirection,
                    density = this
                )
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (if (isDark) 0.26f else 0.70f) * intensity),
                            LiquidGlassCyan.copy(alpha = (if (isDark) 0.18f else 0.28f) * intensity),
                            LiquidGlassPrismViolet.copy(alpha = (if (isDark) 0.14f else 0.22f) * intensity),
                            LiquidGlassAqua.copy(alpha = (if (isDark) 0.18f else 0.30f) * intensity)
                        )
                    ),
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }
    }

    /**
     * Adds an optical frosted glass top specular sheen and subtle chromatic edge rim highlight
     * across Cyber Slate, Liquid Glass, and Material 3 Expressive cards when blur is enabled.
     * Foreground content stays razor-sharp with zero text blurring.
     */
    fun Modifier.frostedChromaticEdge(
        shape: Shape = RoundedCornerShape(20.dp),
        borderAccent: Color? = null
    ): Modifier = composed {
        val blurConfig = LocalBlurEngineConfig.current
        if (blurConfig.style == BlurEngineStyle.OFF ||
            blurConfig.themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE
        ) {
            return@composed this
        }
        val isDark = LocalIsDarkTheme.current
        val isLiquid = blurConfig.themeStyle == NetGateThemeStyle.LIQUID_GLASS
        val frostScale = when (blurConfig.style) {
            BlurEngineStyle.OFF -> 0f
            BlurEngineStyle.SOFT_GLASS -> 0.55f
            BlurEngineStyle.ACRYLIC_PRO -> 0.82f
            BlurEngineStyle.ULTRA_FROST -> 1.0f
            BlurEngineStyle.MORPH -> 1.12f
        }
        val primaryEdge = borderAccent ?: if (isLiquid) LiquidGlassCyan else MaterialTheme.colorScheme.primary
        val secondaryEdge = if (isLiquid) LiquidGlassPrismViolet else MaterialTheme.colorScheme.secondary

        this
            .clip(shape)
            .drawWithContent {
                drawContent()
                val bandHeight = (size.height * 0.28f).coerceIn(8.dp.toPx(), 32.dp.toPx())
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (if (isDark) 0.08f else 0.26f) * frostScale),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = bandHeight
                    )
                )
                val outline = shape.createOutline(
                    size = Size(size.width, size.height),
                    layoutDirection = layoutDirection,
                    density = this
                )
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (if (isDark) 0.22f else 0.60f) * frostScale),
                            primaryEdge.copy(alpha = (if (isDark) 0.16f else 0.22f) * frostScale),
                            secondaryEdge.copy(alpha = (if (isDark) 0.12f else 0.16f) * frostScale),
                            Color.White.copy(alpha = (if (isDark) 0.08f else 0.24f) * frostScale)
                        )
                    ),
                    style = Stroke(width = 1.15.dp.toPx())
                )
            }
    }

    /**
     * Physics-driven spring touch feedback inspired by Expressive-launcher:
     * - Liquid Glass stretches fluidly horizontally and squishes vertically on press.
     * - Material 3 Expressive uses a joyful bouncy spring squash-and-rebound.
     * - Other modes stay crisp without intercepting clicks or scrolls.
     */
    fun Modifier.expressiveLiquidTouch(
        enabled: Boolean = true
    ): Modifier = composed {
        val motion = LocalExpressiveMotion.current
        val blurConfig = LocalBlurEngineConfig.current
        if (!enabled || !blurConfig.animateMotion) {
            return@composed this
        }

        var isPressed by remember { mutableStateOf(false) }

        val targetScaleX = when {
            !isPressed -> 1f
            motion.themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 1.024f
            motion.themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 0.968f
            motion.themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.992f
            else -> 0.985f
        }
        val targetScaleY = when {
            !isPressed -> 1f
            motion.themeStyle == NetGateThemeStyle.LIQUID_GLASS -> 0.962f
            motion.themeStyle == NetGateThemeStyle.MATERIAL_3_EXPRESSIVE -> 0.968f
            motion.themeStyle == NetGateThemeStyle.MATERIAL_YOU_LITE -> 0.992f
            else -> 0.985f
        }

        val springSpec = spring<Float>(
            dampingRatio = motion.spatialDampingRatio,
            stiffness = motion.spatialStiffness
        )
        val scaleX by animateFloatAsState(
            targetValue = targetScaleX,
            animationSpec = springSpec,
            label = "expressiveTouchScaleX"
        )
        val scaleY by animateFloatAsState(
            targetValue = targetScaleY,
            animationSpec = springSpec,
            label = "expressiveTouchScaleY"
        )

        this
            .pointerInput(motion.themeStyle) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    isPressed = true
                    try {
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    } finally {
                        isPressed = false
                    }
                }
            }
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
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
