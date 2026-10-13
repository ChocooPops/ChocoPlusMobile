package com.chocoplusmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Horizontal poster carousel — mirrors the RN app's components/PosterRow.tsx
 * (a windowed FlatList there; LazyRow gives the same "only mounts what's
 * near the visible window" behaviour here for free). */
@Composable
fun <T> PosterRow(
    data: List<T>,
    key: (T, Int) -> Any,
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = 7.dp,
    itemContent: @Composable (T, Int) -> Unit,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        items(count = data.size, key = { i -> key(data[i], i) }) { i ->
            itemContent(data[i], i)
        }
    }
}
