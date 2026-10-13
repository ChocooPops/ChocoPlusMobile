package com.chocoplusmobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chocoplusmobile.R
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors

/**
 * Mirrors the RN app's components/TopBar.tsx. `overlay` mode floats the bar
 * above hero art and fades in a solid background as the user scrolls (Home /
 * Films / Séries / LicenseDetail) — driven here by the same LazyListState
 * already scrolling the screen's content, rather than a separate
 * Animated.Value.
 */
@Composable
fun TopBar(
    onSearch: () -> Unit,
    onAvatar: () -> Unit,
    avatarInitial: String,
    modifier: Modifier = Modifier,
    avatarUri: String? = null,
    searchActive: Boolean = false,
    onToggleFormat: (() -> Unit)? = null,
    formatIsVertical: Boolean = false,
    onToggleAlignment: (() -> Unit)? = null,
    alignmentIcon: ImageVector? = null,
    overlay: Boolean = false,
    listState: LazyListState? = null,
) {
    val scrollPx = if (overlay && listState != null) {
        if (listState.firstVisibleItemIndex > 0) 10_000 else listState.firstVisibleItemScrollOffset
    } else {
        0
    }
    val backgroundAlpha = if (!overlay) 1f else if (scrollPx <= 16) 0f else (((scrollPx - 16f) / 44f).coerceIn(0f, 1f) * 0.97f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (overlay) ChocoColors.surface.copy(alpha = backgroundAlpha) else ChocoColors.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 0.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = null,
                modifier = Modifier.height(26.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircleButton(
                    icon = AppIcon.of("search"),
                    onClick = onSearch,
                    size = 40.dp,
                    iconSize = 23.dp,
                    color = if (searchActive) ChocoColors.accent else ChocoColors.text,
                    background = Color.Transparent,
                )
                if (onToggleFormat != null) {
                    FormatToggleButton(vertical = !formatIsVertical, onClick = onToggleFormat, size = 40.dp, iconHeight = 18.dp)
                }
                if (onToggleAlignment != null && alignmentIcon != null) {
                    IconCircleButton(icon = alignmentIcon, onClick = onToggleAlignment, size = 40.dp, iconSize = 22.dp, background = Color.Transparent)
                }
                Avatar(
                    initial = avatarInitial,
                    uri = avatarUri,
                    size = 34.dp,
                    radius = 5.dp,
                    onClick = onAvatar,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
