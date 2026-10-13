package com.chocoplusmobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

/** Plain tap navigates (onClick); long-press opens the quick-actions
 * preview (onLongPress) — additive, mirrors every poster card's
 * onPress/onLongPress pair in the RN app. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.longPressable(onClick: () -> Unit, onLongPress: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongPress)
