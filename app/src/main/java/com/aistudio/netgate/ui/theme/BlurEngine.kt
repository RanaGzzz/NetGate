package com.aistudio.netgate.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
        val primaryEdge = borderAccent ?: if (isLiquid) LiquidGlassCyan else EmeraldBright
        val secondaryEdge = if (isLiquid) LiquidGlassPrismViolet else LiquidGlassCyan

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
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    isPressed = false
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
