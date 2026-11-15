package com.example.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalIsDarkTheme

@Composable
fun FrostedBackdrop(
    isFirewallActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val baseColor = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        // Blurred dynamic Material You / semantic orbs behind frosted panels
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(64.dp)
        ) {
            val primaryGlow = if (isFirewallActive) primary else secondary
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = if (isDark) 0.28f else 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.14f),
                    radius = size.maxDimension * 0.55f
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(size.width * 0.15f, size.height * 0.14f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondary.copy(alpha = if (isDark) 0.22f else 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.34f),
                    radius = size.maxDimension * 0.48f
                ),
                radius = size.maxDimension * 0.48f,
                center = Offset(size.width * 0.88f, size.height * 0.34f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = if (isDark) 0.22f else 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.30f, size.height * 0.84f),
                    radius = size.maxDimension * 0.52f
                ),
                radius = size.maxDimension * 0.52f,
                center = Offset(size.width * 0.30f, size.height * 0.84f)
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
    val glassFill = tintColor ?: if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }

    val borderBrush = if (borderAccent != null) {
        Brush.linearGradient(
            colors = listOf(
                borderAccent.copy(alpha = 0.68f),
                borderAccent.copy(alpha = 0.24f)
            )
        )
    } else if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.26f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.94f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        )
    }

    val sheenBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.02f),
                Color.Transparent
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.50f),
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
