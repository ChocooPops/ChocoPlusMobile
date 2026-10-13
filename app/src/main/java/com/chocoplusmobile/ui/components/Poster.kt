package com.chocoplusmobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

object PosterRatio {
    const val R2_3 = 2f / 3f
    const val R16_9 = 16f / 9f
    const val R3_4 = 3f / 4f
    const val R16_10 = 16f / 10f
    const val R16_11 = 16f / 11f
    const val R1_1 = 1f
}

/**
 * Poster/backdrop tile used everywhere in the app (rows, grids, hero, detail
 * headers…) — mirrors the RN app's components/Poster.tsx. While `uri` is
 * empty — or the image fails to load — it falls back to a transparent tile
 * with a grey border and the title centered inside a translucent black
 * chip, so the fallback reads clearly as a placeholder rather than
 * mimicking a real poster layout.
 *
 * While a real `uri` is still being fetched, a plain grey block with the
 * title shows instead (a different look from the "no image" fallback above,
 * so a slow load never reads as "this title has no poster") — the image
 * then fades in over it once decoded, rather than popping in abruptly.
 */
@Composable
fun Poster(
    title: String,
    uri: String?,
    modifier: Modifier = Modifier,
    ratio: Float = PosterRatio.R2_3,
    radius: Dp = 5.dp,
    badge: String? = null,
    progress: Double? = null,
    showPlayIcon: Boolean = false,
    titleSize: TextUnit = 13.sp,
    hideFallbackTitle: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var failed by remember(uri) { mutableStateOf(false) }
    var loaded by remember(uri) { mutableStateOf(false) }
    val showFallback = uri.isNullOrEmpty() || failed
    val shape = RoundedCornerShape(radius)
    val imageAlpha by animateFloatAsState(targetValue = if (loaded) 1f else 0f, animationSpec = tween(280), label = "posterFade")

    Box(
        modifier = modifier
            .aspectRatio(ratio)
            .shadow(4.dp, shape, clip = false, ambientColor = ChocoColors.posterShadow, spotColor = ChocoColors.posterShadow)
            .clip(shape)
            .background(if (showFallback) Color.Transparent else ChocoColors.surfaceAlt)
            .then(if (showFallback) Modifier.border(0.5.dp, ChocoColors.posterEmptyBorder, shape) else Modifier),
    ) {
        if (showFallback) {
            if (!hideFallbackTitle) {
                Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .background(ChocoColors.posterEmptyLabelBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = title,
                            color = ChocoColors.posterEmptyLabelText,
                            fontFamily = ChocoFonts.headingExtraBold,
                            fontSize = titleSize,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            if (!loaded) {
                Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xCC141414), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontFamily = ChocoFonts.bodyBold,
                            fontSize = titleSize * 1.15f,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            AsyncImage(
                model = uri,
                contentDescription = title,
                modifier = Modifier.fillMaxSize().alpha(imageAlpha),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { failed = true },
            )
            if (badge != null || progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x8C000000)))),
                )
            }
        }

        if (showPlayIcon && !showFallback) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color(0xEBFFFFFF),
                modifier = Modifier.align(Alignment.Center).height(34.dp).aspectRatio(1f),
            )
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(text = badge, color = ChocoColors.accent, fontSize = 8.5.sp, fontFamily = ChocoFonts.headingExtraBold, fontWeight = FontWeight.Bold)
            }
        }

        if (progress != null) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x38FFFFFF))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (progress.toFloat() / 100f).coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(ChocoColors.accent),
                )
            }
        }

        content()
    }
}
