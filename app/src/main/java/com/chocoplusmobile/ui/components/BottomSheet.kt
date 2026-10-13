package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.chocoplusmobile.ui.theme.ChocoColors

/**
 * Generic remounting bottom sheet — mirrors the RN app's
 * components/BottomSheet.tsx. `scrollable = false` is for callers whose
 * content manages its own scrolling/windowing (a LazyColumn/LazyVerticalGrid)
 * — same reasoning as the RN side's ScrollView-vs-FlatList nesting note:
 * content sits directly in the height-bounded Box either way, so a lazy
 * child can pick up that bound as its own scroll viewport instead of being
 * double-wrapped in a second scrolling container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChocoBottomSheet(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightPct: Float = 0.86f,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * maxHeightPct
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = ChocoColors.surfaceAlt,
        contentColor = ChocoColors.text,
        dragHandle = {
            Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(999.dp)))
            }
        },
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 18.dp)) {
            if (scrollable) {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}
