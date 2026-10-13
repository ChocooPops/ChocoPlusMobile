package com.chocoplusmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.data.MetadataRow
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

/** Mirrors the RN app's components/MetadataSheet.tsx — the simple key/value
 * "sync_alt" metadata button on MovieDetail/SerieDetail. */
@Composable
fun MetadataSheet(visible: Boolean, onClose: () -> Unit, rows: List<MetadataRow>, loading: Boolean = false) {
    ChocoBottomSheet(visible = visible, onClose = onClose, maxHeightPct = 0.8f) {
        Text("Métadonnées", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 19.sp)
        Text(
            "L'édition reste réservée aux administrateurs, sur desktop.",
            color = ChocoColors.textMuted,
            fontFamily = ChocoFonts.bodyRegular,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )
        if (loading) {
            CircularProgressIndicator(color = ChocoColors.accent, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                rows.forEach { r -> MetadataRowLine(r) }
            }
        }
        PrimaryButton(label = "Fermer", variant = ButtonVariant.OUTLINE, onClick = onClose, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
    }
}

@Composable
fun MetadataRowLine(row: MetadataRow) {
    val dividerColor = ChocoColors.divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .drawBehind {
                drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(row.k, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, modifier = Modifier.width(96.dp))
        Text(row.v, color = ChocoColors.textSecondary, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}
