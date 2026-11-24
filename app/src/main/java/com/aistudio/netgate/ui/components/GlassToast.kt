package com.aistudio.netgate.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Short in-app status message that replaces the bottom Snackbar.
 *
 * It floats above the app content, directly under the status bar, so it never covers the
 * floating navigation bar or the bottom controls. Rendering goes through [FrostedSurface],
 * which means the pill automatically adopts the Liquid Glass refraction/sheen treatment and
 * the tonal surfaces of the other themes.
 */
object GlassToast {
    internal var text by mutableStateOf<String?>(null)

    /** Grows with every message so the same text shown twice in a row is displayed again. */
    internal var serial by mutableIntStateOf(0)

    fun show(message: String) {
        text = message
        serial++
    }
}

@Composable
fun GlassToastHost(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val serial = GlassToast.serial

    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        visible = true
        delay(3000L)
        visible = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(170)) +
                slideInVertically(spring(dampingRatio = 0.72f, stiffness = 480f)) { -it / 2 } +
                scaleIn(spring(dampingRatio = 0.72f, stiffness = 480f), initialScale = 0.92f),
            exit = fadeOut(tween(150)) +
                slideOutVertically(tween(180)) { -it / 3 }
        ) {
            FrostedSurface(
                shape = RoundedCornerShape(24.dp),
                borderAccent = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                elevation = 10.dp,
                modifier = Modifier
                    .widthIn(max = 430.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clickable { visible = false }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                    Text(
                        text = GlassToast.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
