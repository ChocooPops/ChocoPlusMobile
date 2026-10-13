package com.chocoplusmobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.isSpecified
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import com.chocoplusmobile.data.Palette
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

// ── Chip ──

@Composable
fun ChocoChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    white: Boolean = false,
    small: Boolean = false,
) {
    val activeBg = if (white) ChocoColors.text else ChocoColors.accent
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) activeBg else Color(0x0AFFFFFF))
            .border(1.dp, if (active) activeBg else ChocoColors.borderStrong, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 11.dp else 14.dp, vertical = if (small) 6.5.dp else 8.dp),
    ) {
        Text(
            text = label,
            color = if (active) ChocoColors.onAccent else ChocoColors.textTertiary,
            fontFamily = if (active) ChocoFonts.bodyBold else ChocoFonts.bodyMedium,
            fontSize = if (small) 12.sp else 12.5.sp,
        )
    }
}

// ── PrimaryButton ──

enum class ButtonVariant { ACCENT, OUTLINE, GHOST }

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: ButtonVariant = ButtonVariant.ACCENT,
    height: Dp = 48.dp,
) {
    val isAccent = variant == ButtonVariant.ACCENT
    val bg = when {
        isAccent && !enabled -> ChocoColors.accentDim
        isAccent -> ChocoColors.accent
        variant == ButtonVariant.GHOST -> Color(0x1AFFFFFF)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(if (height >= 50.dp) 12.dp else 7.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (variant == ButtonVariant.OUTLINE) Modifier.border(1.dp, ChocoColors.borderStrong, shape) else Modifier)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = if (isAccent) ChocoColors.onAccent else ChocoColors.text, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = if (isAccent) ChocoColors.onAccent else ChocoColors.text, modifier = Modifier.size(15.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(7.dp))
                }
                Text(label, color = if (isAccent) ChocoColors.onAccent else ChocoColors.text, fontFamily = ChocoFonts.bodyBold, fontSize = 15.sp)
            }
        }
    }
}

// ── IconCircleButton ──

@Composable
fun IconCircleButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    color: Color = ChocoColors.text,
    background: Color = Color(0x73000000),
) {
    IconCircleButtonSlot(modifier = modifier, onClick = onClick, size = size, background = background) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

/** Same shape/behaviour as IconCircleButton, but the glyph is supplied by
 * the caller — used for custom (non-Material) icons like DetailIcon. */
@Composable
fun IconCircleButtonSlot(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    size: Dp = 40.dp,
    background: Color = Color(0x73000000),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Square (not circular), bordered icon button — used for the "Ma liste"
 * add/remove toggle everywhere it appears (movie/serie detail, the poster
 * preview sheet, Home's hero), as a deliberate visual departure from the
 * round IconCircleButton used for every other icon action nearby. */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    // Most icons here are square already (Material glyphs, the plus vector
    // below) and just want `iconSize` applied uniformly — but MyListCheckVector
    // isn't square (a checkmark's natural bounds are wider than they are
    // tall), so forcing it into a square box would visibly squash it.
    // Callers with a non-square icon pass their own aspect-correct modifier.
    iconModifier: Modifier = Modifier.size(iconSize),
    color: Color = ChocoColors.text,
    background: Color = Color.Transparent,
    borderColor: Color = Color(0x40FFFFFF),
    radius: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(radius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = iconModifier)
    }
}

// ── ProgressBar ──

@Composable
fun ChocoProgressBar(
    progress: Double,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    trackColor: Color = Color(0x26FFFFFF),
    fillColor: Color = ChocoColors.accent,
) {
    val fraction = (progress / 100.0).coerceIn(0.0, 1.0).toFloat()
    Box(modifier = modifier.height(height).clip(RoundedCornerShape(999.dp)).background(trackColor)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(fillColor, RoundedCornerShape(999.dp)),
        )
    }
}

// ── Avatar ──

@Composable
fun Avatar(
    initial: String,
    uri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 35.dp,
    radius: Dp = size / 2,
    onClick: (() -> Unit)? = null,
    editable: Boolean = false,
) {
    var failed by remember(uri) { mutableStateOf(false) }
    val showPhoto = !uri.isNullOrEmpty() && !failed
    val shape = RoundedCornerShape(radius)
    Box(modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        if (showPhoto) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.size(size).clip(shape),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFFD301), Color(0xFF8A5A00)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = ChocoColors.onAccent, fontFamily = ChocoFonts.headingExtraBold, fontSize = (size.value * 0.42f).sp)
            }
        }
        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ChocoColors.surface)
                    .border(1.dp, ChocoColors.borderStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(com.chocoplusmobile.ui.icons.AppIcon.of("edit"), contentDescription = null, tint = ChocoColors.text, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── FadeInImage ──

/** Same loading treatment as Poster's own (grey block + title, then a fade
 * once decoded) for the big background/backdrop images that don't go
 * through Poster's poster-shaped chrome — hero banners, license backdrops.
 * `title` is optional since some backdrops don't have a natural one handy
 * at the call site; the grey block still shows without it. */
@Composable
fun FadeInImage(uri: String, title: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var loaded by remember(uri) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(targetValue = if (loaded) 1f else 0f, animationSpec = tween(280), label = "backdropFade")
    Box(modifier = modifier) {
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize().background(ChocoColors.surfaceAlt).padding(24.dp), contentAlignment = Alignment.Center) {
                if (!title.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xCC141414), RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontFamily = ChocoFonts.bodyBold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(imageAlpha),
            contentScale = contentScale,
            onSuccess = { loaded = true },
        )
    }
}

// ── LogoImage ──

/** A logo rendered at its true aspect ratio instead of stretched to fill a
 * fixed-width box — mirrors the RN app's components/LogoImage.tsx.
 * `ContentScale.Fit` centers content within whatever box you give it — fine
 * for a wide wordmark that nearly fills the box, but a square-ish logo ends
 * up with lots of empty space on both sides. Reading the painter's own
 * intrinsic size lets the box hug the logo instead. */
@Composable
fun LogoImage(uri: String, height: Dp, maxWidth: Dp, modifier: Modifier = Modifier) {
    val painter = rememberAsyncImagePainter(uri)
    val intrinsic = painter.intrinsicSize
    val width = if (intrinsic.isSpecified && intrinsic.height > 0f) {
        (height.value * (intrinsic.width / intrinsic.height)).dp.coerceAtMost(maxWidth)
    } else {
        maxWidth
    }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.width(width).height(height),
        contentScale = ContentScale.Fit,
    )
}

// ── PersonCard ──

@Composable
fun PersonCard(name: String, role: String?, photoUrl: String?, modifier: Modifier = Modifier, size: Dp = 74.dp, radius: Dp = 14.dp) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    androidx.compose.foundation.layout.Column(modifier = modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name,
                modifier = Modifier.size(size).clip(RoundedCornerShape(radius)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(radius))
                    .background(Brush.linearGradient(listOf(Palette.accentTint(name), Color(0xFF2A2A2A)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, color = Color(0xD9FFFFFF), fontFamily = ChocoFonts.headingExtraBold, fontSize = (size.value * 0.3f).sp)
            }
        }
        Text(
            name,
            color = Color(0xD9FFFFFF),
            fontFamily = ChocoFonts.bodyRegular,
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 7.dp),
        )
        if (!role.isNullOrEmpty()) {
            Text(
                role,
                color = Color(0x80FFFFFF),
                fontFamily = ChocoFonts.bodyRegular,
                fontSize = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
