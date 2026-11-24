package com.aistudio.netgate.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.data.model.BackgroundAnimationStyle
import com.aistudio.netgate.ui.theme.LocalIsDarkTheme
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ambient animation painted behind every NetGate screen, above the aurora backdrop and below all
 * content. It is a single [Canvas]; the animated phase is read inside the draw scope so the
 * composition never re-runs while the animation plays.
 */
@Composable
fun AnimatedAppBackground(
    style: BackgroundAnimationStyle,
    parallaxEnabled: Boolean,
    seasonalEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (style == BackgroundAnimationStyle.NONE) return

    // "Random" is resolved once per screen lifetime, not per frame.
    val resolved = remember(style) {
        if (style == BackgroundAnimationStyle.RANDOM) {
            BackgroundAnimationStyle.entries
                .filter { it != BackgroundAnimationStyle.RANDOM && it != BackgroundAnimationStyle.NONE }
                .random()
        } else {
            style
        }
    }
    val phase = backgroundPhase()
    val parallax = rememberParallaxOffset(parallaxEnabled)
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = seasonalAccent(seasonalEnabled, MaterialTheme.colorScheme.secondary)
    val tertiary = MaterialTheme.colorScheme.tertiary
    val alphaScale = if (isDark) 1f else 0.72f
    val lineWidth = 1.4.dp

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = phase.value
        translate(parallax.x.dp.toPx(), parallax.y.dp.toPx()) {
            when (resolved) {
                BackgroundAnimationStyle.CIRCLES -> drawCircles(t, primary, secondary, alphaScale, lineWidth.toPx())
                BackgroundAnimationStyle.RINGS -> drawRings(t, primary, secondary, alphaScale, lineWidth.toPx())
                BackgroundAnimationStyle.MESH -> drawMesh(t, primary, secondary, tertiary, alphaScale)
                BackgroundAnimationStyle.SPACE -> drawSpace(t, primary, secondary, alphaScale)
                BackgroundAnimationStyle.SHAPES -> drawShapes(t, primary, secondary, tertiary, alphaScale, lineWidth.toPx())
                BackgroundAnimationStyle.SNOW -> drawSnow(t, primary, secondary, alphaScale)
                BackgroundAnimationStyle.GRID -> drawGrid(t, primary, alphaScale)
                BackgroundAnimationStyle.PARTICLES -> drawParticles(t, primary, secondary, alphaScale, lineWidth.toPx())
                BackgroundAnimationStyle.LAVA_LAMP -> drawLavaLamp(t, primary, secondary, tertiary, alphaScale)
                BackgroundAnimationStyle.NONE, BackgroundAnimationStyle.RANDOM -> Unit
            }
        }
    }
}

@Composable
private fun backgroundPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "appBackground")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "appBackgroundPhase"
    )
}

/** Device tilt used by the parallax toggle; values are in dp so the draw scope can scale them. */
@Composable
private fun rememberParallaxOffset(enabled: Boolean): Offset {
    var offset by remember { mutableStateOf(Offset.Zero) }
    if (!enabled) return Offset.Zero
    val context = LocalContext.current
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val maxDp = 9f
                offset = Offset(
                    x = (-event.values[0] / SensorManager.GRAVITY_EARTH * maxDp).coerceIn(-maxDp, maxDp),
                    y = (event.values[1] / SensorManager.GRAVITY_EARTH * maxDp).coerceIn(-maxDp, maxDp)
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return offset
}

private fun DrawScope.drawCircles(
    t: Float,
    primary: Color,
    secondary: Color,
    alphaScale: Float,
    strokeWidth: Float
) {
    val twoPi = (2.0 * PI).toFloat()
    repeat(7) { index ->
        val seed = index * 1.37f
        val x = size.width * (0.12f + 0.76f * ((0.5f + 0.5f * sin(twoPi * t + seed)) * 0.7f + 0.15f))
        val y = size.height * (0.10f + 0.80f * ((0.5f + 0.5f * cos(twoPi * t * 0.8f + seed)) * 0.7f + 0.15f))
        val radius = size.minDimension * (0.05f + 0.035f * (1f + sin(twoPi * t + index)))
        val color = if (index % 2 == 0) primary else secondary
        drawCircle(
            color = color.copy(alpha = 0.16f * alphaScale),
            radius = radius,
            center = Offset(x, y)
        )
        drawCircle(
            color = color.copy(alpha = 0.30f * alphaScale),
            radius = radius * 1.18f,
            center = Offset(x, y),
            style = Stroke(width = strokeWidth)
        )
    }
}

private fun DrawScope.drawRings(
    t: Float,
    primary: Color,
    secondary: Color,
    alphaScale: Float,
    strokeWidth: Float
) {
    val twoPi = (2.0 * PI).toFloat()
    val center = Offset(
        x = size.width * (0.5f + 0.16f * sin(twoPi * t)),
        y = size.height * (0.46f + 0.14f * cos(twoPi * t * 0.9f))
    )
    repeat(5) { index ->
        val radius = size.minDimension * (0.13f + index * 0.10f + 0.02f * sin(twoPi * t + index))
        drawCircle(
            color = (if (index % 2 == 0) primary else secondary)
                .copy(alpha = (0.26f - index * 0.035f) * alphaScale),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
    }
    drawCircle(
        color = primary.copy(alpha = 0.16f * alphaScale),
        radius = size.minDimension * 0.035f,
        center = center
    )
}

private fun DrawScope.drawMesh(
    t: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    alphaScale: Float
) {
    val twoPi = (2.0 * PI).toFloat()
    data class Blob(val color: Color, val baseX: Float, val baseY: Float, val speed: Float, val radius: Float)
    val blobs = listOf(
        Blob(primary, 0.20f, 0.18f, 1.0f, 0.62f),
        Blob(secondary, 0.82f, 0.34f, 0.8f, 0.58f),
        Blob(tertiary, 0.34f, 0.82f, 1.2f, 0.60f),
        Blob(primary, 0.72f, 0.86f, 0.9f, 0.44f)
    )
    blobs.forEachIndexed { index, blob ->
        val px = size.width * (blob.baseX + 0.10f * sin(twoPi * t * blob.speed + index))
        val py = size.height * (blob.baseY + 0.09f * cos(twoPi * t * blob.speed * 0.8f + index))
        val radius = size.maxDimension * blob.radius
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    blob.color.copy(alpha = 0.20f * alphaScale),
                    blob.color.copy(alpha = 0.06f * alphaScale),
                    Color.Transparent
                ),
                center = Offset(px, py),
                radius = radius
            ),
            radius = radius,
            center = Offset(px, py)
        )
    }
}

private fun DrawScope.drawSpace(
    t: Float,
    primary: Color,
    secondary: Color,
    alphaScale: Float
) {
    val random = Random(2026)
    val twoPi = (2.0 * PI).toFloat()
    repeat(70) { index ->
        val baseX = random.nextFloat()
        val baseY = random.nextFloat()
        val drift = 0.01f * sin(twoPi * t + index * 0.7f)
        val x = size.width * ((baseX + drift + 1f) % 1f)
        val y = size.height * baseY
        val twinkle = 0.25f + 0.75f * (0.5f + 0.5f * sin(twoPi * (t * 2f) + index * 1.9f))
        val radius = (0.7f + (index % 4) * 0.55f).dp.toPx()
        drawCircle(
            color = (if (index % 5 == 0) secondary else primary)
                .copy(alpha = 0.55f * twinkle * alphaScale),
            radius = radius,
            center = Offset(x, y)
        )
        if (index % 17 == 0) {
            val sparkle = 5.dp.toPx()
            drawLine(
                color = secondary.copy(alpha = 0.35f * twinkle * alphaScale),
                start = Offset(x - sparkle, y),
                end = Offset(x + sparkle, y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = secondary.copy(alpha = 0.35f * twinkle * alphaScale),
                start = Offset(x, y - sparkle),
                end = Offset(x, y + sparkle),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawShapes(
    t: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    alphaScale: Float,
    strokeWidth: Float
) {
    val twoPi = (2.0 * PI).toFloat()
    val colors = listOf(primary, secondary, tertiary)
    repeat(4) { index ->
        val sides = 3 + index
        val radius = size.minDimension * (0.09f + index * 0.025f)
        val cx = size.width * (0.22f + 0.18f * index)
        val cy = size.height * (0.28f + 0.16f * (index % 2)) +
            size.height * 0.04f * sin(twoPi * t + index)
        val path = Path()
        repeat(sides) { corner ->
            val angle = twoPi * corner / sides - (PI / 2).toFloat()
            val px = cx + radius * cos(angle)
            val py = cy + radius * sin(angle)
            if (corner == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        rotate(
            degrees = t * 360f * (if (index % 2 == 0) 1f else -1f) * 0.25f,
            pivot = Offset(cx, cy)
        ) {
            drawPath(
                path = path,
                color = colors[index % colors.size].copy(alpha = 0.30f * alphaScale),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

private fun DrawScope.drawSnow(
    t: Float,
    primary: Color,
    secondary: Color,
    alphaScale: Float
) {
    val random = Random(1201)
    val twoPi = (2.0 * PI).toFloat()
    repeat(60) { index ->
        val startX = random.nextFloat()
        val startY = random.nextFloat()
        val speed = 0.35f + random.nextFloat() * 0.6f
        val y = ((startY + t * speed) % 1.08f) * size.height - 0.04f * size.height
        val x = size.width * startX + size.width * 0.03f * sin(twoPi * t * 2f + index)
        val radius = (1.0f + (index % 3) * 0.8f).dp.toPx()
        drawCircle(
            color = (if (index % 4 == 0) secondary else primary).copy(alpha = 0.42f * alphaScale),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawGrid(
    t: Float,
    primary: Color,
    alphaScale: Float
) {
    val twoPi = (2.0 * PI).toFloat()
    val spacing = 30.dp.toPx()
    val columns = (size.width / spacing).toInt().coerceAtLeast(1) + 1
    val rows = (size.height / spacing).toInt().coerceAtLeast(1) + 1
    val center = Offset(size.width / 2f, size.height / 2f)
    for (column in 0 until columns) {
        for (row in 0 until rows) {
            val x = column * spacing
            val y = row * spacing
            val distance = hypot(x - center.x, y - center.y) / size.minDimension
            val pulse = 0.5f + 0.5f * sin(twoPi * t * 2f - distance * 12f)
            drawCircle(
                color = primary.copy(alpha = (0.12f + 0.32f * pulse) * alphaScale),
                radius = (1.2f + 1.4f * pulse).dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawParticles(
    t: Float,
    primary: Color,
    secondary: Color,
    alphaScale: Float,
    strokeWidth: Float
) {
    val random = Random(4049)
    val twoPi = (2.0 * PI).toFloat()
    val points = List(24) { index ->
        val baseX = random.nextFloat()
        val baseY = random.nextFloat()
        Offset(
            x = size.width * (baseX + 0.06f * sin(twoPi * t + index * 0.9f)),
            y = size.height * (baseY + 0.05f * cos(twoPi * t * 0.8f + index * 1.3f))
        )
    }
    points.forEachIndexed { index, point ->
        drawCircle(
            color = (if (index % 3 == 0) secondary else primary).copy(alpha = 0.55f * alphaScale),
            radius = 1.8.dp.toPx(),
            center = point
        )
    }
    val maxDistance = size.minDimension * 0.16f
    for (first in points.indices) {
        for (second in first + 1 until points.size) {
            val distance = (points[first] - points[second]).getDistance()
            if (distance < maxDistance) {
                drawLine(
                    color = primary.copy(alpha = 0.22f * (1f - distance / maxDistance) * alphaScale),
                    start = points[first],
                    end = points[second],
                    strokeWidth = strokeWidth * 0.6f
                )
            }
        }
    }
}

private fun DrawScope.drawLavaLamp(
    t: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    alphaScale: Float
) {
    val twoPi = (2.0 * PI).toFloat()
    val blobs = listOf(
        Triple(primary, 0.28f, 0.24f),
        Triple(secondary, 0.74f, 0.36f),
        Triple(tertiary, 0.42f, 0.76f)
    )
    blobs.forEachIndexed { index, (color, baseX, baseY) ->
        val x = size.width * (baseX + 0.10f * sin(twoPi * t * 0.6f + index * 1.7f))
        val y = size.height * (baseY + 0.10f * cos(twoPi * t * 0.5f + index * 2.1f))
        val radius = size.minDimension * (0.24f + 0.06f * sin(twoPi * t + index))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.28f * alphaScale),
                    color.copy(alpha = 0.10f * alphaScale),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = radius
            ),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

/** Seasonal tint applied to the animation when the toggle is on: spring, summer, autumn, winter. */
@Composable
private fun seasonalAccent(enabled: Boolean, fallback: Color): Color {
    if (!enabled) return fallback
    return remember {
        when (Calendar.getInstance().get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Color(0xFF7DD3FC)
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Color(0xFF86EFAC)
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Color(0xFFFDE68A)
            else -> Color(0xFFFDBA74)
        }
    }
}
