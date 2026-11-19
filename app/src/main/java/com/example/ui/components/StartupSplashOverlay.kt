package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlurEngine.blurEngineBackdrop
import com.example.ui.theme.LocalBlurEngineConfig
import com.example.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StartupSplashOverlay(
    isFirewallActive: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val blurConfig = LocalBlurEngineConfig.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val backgroundColor = MaterialTheme.colorScheme.background

    val emblemScale = remember { Animatable(0.38f) }
    val emblemAlpha = remember { Animatable(0f) }
    val pulseProgress = remember { Animatable(0f) }
    val sweepAngle = remember { Animatable(0f) }
    val textProgress = remember { Animatable(0f) }
    val barProgress = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }
    val overlayScale = remember { Animatable(1f) }

    var statusStep by remember { mutableIntStateOf(0) }
    var isExiting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val triggerExit: () -> Unit = remember {
        {
            if (!isExiting) {
                isExiting = true
                scope.launch {
                    launch {
                        overlayScale.animateTo(
                            targetValue = 1.04f,
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        )
                    }
                    overlayAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )
                    onDismissed()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launch {
            emblemAlpha.animateTo(1f, tween(durationMillis = 260))
        }
        launch {
            emblemScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            pulseProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing)
            )
        }
        launch {
            sweepAngle.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
            )
        }
        launch {
            delay(160L)
            textProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(180L)
            barProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 980, easing = FastOutSlowInEasing)
            )
        }

        delay(420L)
        statusStep = 1
        delay(440L)
        statusStep = 2
        delay(320L)
        triggerExit()
    }

    val statusText = when (statusStep) {
        0 -> "Initializing Blur Engine…"
        1 -> "Arming Firewall & DNS Rules…"
        else -> if (isFirewallActive) "NetGate Shield Active" else "NetGate Shield Ready"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha.value
                scaleX = overlayScale.value
                scaleY = overlayScale.value
            }
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = triggerExit
            )
            .testTag("startup_splash_overlay")
    ) {
        // Blurred radial aura synced with active BlurEngine style
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blurEngineBackdrop(
                    radius = blurConfig.style.backdropRadiusDp.coerceAtLeast(36f).dp,
                    saturation = blurConfig.style.saturationBoost
                )
        ) {
            val auraScale = 0.72f + 0.28f * emblemAlpha.value
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = (if (isDark) 0.42f else 0.30f) * emblemAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.65f * auraScale
                ),
                radius = size.minDimension * 0.65f * auraScale,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondary.copy(alpha = (if (isDark) 0.30f else 0.22f) * textProgress.value),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.2f, size.height * 0.24f),
                    radius = size.maxDimension * 0.48f
                ),
                radius = size.maxDimension * 0.48f,
                center = Offset(size.width * 0.2f, size.height * 0.24f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = (if (isDark) 0.26f else 0.18f) * textProgress.value),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.78f),
                    radius = size.maxDimension * 0.46f
                ),
                radius = size.maxDimension * 0.46f,
                center = Offset(size.width * 0.82f, size.height * 0.78f)
            )
        }

        // Top-right Skip button with 48dp accessible hit target
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
        ) {
            TextButton(
                onClick = triggerExit,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("startup_skip_button")
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated shield + expanding telemetry rings
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .semantics { contentDescription = "NetGate animated security shield emblem" },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    val ring1Fraction = pulseProgress.value
                    val ring2Fraction = ((pulseProgress.value - 0.24f) / 0.76f).coerceIn(0f, 1f)

                    if (ring1Fraction > 0f) {
                        val r1 = maxRadius * (0.48f + 0.50f * ring1Fraction)
                        drawCircle(
                            color = primary.copy(alpha = (1f - ring1Fraction) * 0.55f),
                            radius = r1,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                    if (ring2Fraction > 0f) {
                        val r2 = maxRadius * (0.48f + 0.44f * ring2Fraction)
                        drawCircle(
                            color = secondary.copy(alpha = (1f - ring2Fraction) * 0.45f),
                            radius = r2,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Sweeping dual telemetry arcs around the medallion
                    val arcInset = maxRadius * 0.30f
                    val arcSize = Size(
                        width = size.width - arcInset * 2f,
                        height = size.height - arcInset * 2f
                    )
                    val arcAlpha = emblemAlpha.value * (0.85f - 0.25f * pulseProgress.value)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                primary.copy(alpha = arcAlpha),
                                secondary.copy(alpha = arcAlpha * 0.7f),
                                Color.Transparent
                            )
                        ),
                        startAngle = sweepAngle.value - 90f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(arcInset, arcInset),
                        size = arcSize,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                FrostedSurface(
                    shape = RoundedCornerShape(28.dp),
                    tintColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                        alpha = if (isDark) 0.72f else 0.85f
                    ),
                    borderAccent = primary,
                    elevation = 10.dp,
                    modifier = Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            scaleX = emblemScale.value
                            scaleY = emblemScale.value
                            alpha = emblemAlpha.value
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(46.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Wordmark & tagline entrance
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textProgress.value
                    translationY = (1f - textProgress.value) * 20.dp.toPx()
                }
            ) {
                Text(
                    text = "NetGate",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Per-App Firewall • DNS & Ad Shield",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Boot progress beam + milestone pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(240.dp)
                    .graphicsLayer {
                        alpha = textProgress.value
                        translationY = (1f - textProgress.value) * 14.dp.toPx()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barProgress.value.coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(primary, secondary)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                FrostedSurface(
                    shape = RoundedCornerShape(50),
                    borderAccent = primary.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AnimatedContent(
                            targetState = statusText,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                            },
                            label = "startupStatusLabel"
                        ) { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
