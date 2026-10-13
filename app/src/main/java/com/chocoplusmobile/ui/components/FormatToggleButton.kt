package com.chocoplusmobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chocoplusmobile.ui.theme.ChocoColors

/**
 * The Vertical/Horizontal format button — mirrors the RN app's
 * FormatToggleButton.tsx + FormatToggleIcon.tsx: a single rounded-rectangle
 * glyph drawn "at rest" in portrait orientation, rotated 90° for landscape
 * instead of swapping in a second glyph. `vertical` is the CURRENT poster
 * format — when it's vertical, the glyph is shown rotated to landscape,
 * reading as "tap for horizontal" (the action-oriented convention every
 * format icon in this app follows).
 */
@Composable
fun FormatToggleButton(
    vertical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconHeight: Dp = 18.dp,
    color: Color = ChocoColors.text,
    background: Color = Color.Transparent,
) {
    val rotation by animateFloatAsState(
        targetValue = if (vertical) 90f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "formatToggleRotation",
    )
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val iconWidth = iconHeight * (18f / 27f)
        Canvas(modifier = Modifier.size(width = iconWidth, height = iconHeight).rotate(rotation)) {
            // Scale the SVG's native 3px (out of a 27-tall viewBox) stroke to
            // this glyph's actual rendered height — `iconHeight` is captured
            // from the composable above; DrawScope supplies the Density
            // that makes `.toPx()` on it valid here.
            val strokeWidthPx = 3f * (iconHeight.toPx() / 27f)
            val inset = strokeWidthPx / 2f
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(this.size.width - strokeWidthPx, this.size.height - strokeWidthPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * 0.1f),
                style = Stroke(width = strokeWidthPx),
            )
        }
    }
}
