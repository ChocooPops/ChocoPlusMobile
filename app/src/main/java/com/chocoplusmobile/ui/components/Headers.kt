package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

/** Mirrors the RN app's components/SectionHeader.tsx — a "| Title >" row
 * used above every selection carousel. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, accentBar: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 0.dp)
            .padding(bottom = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (accentBar) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(width = 3.dp, height = 16.dp).background(ChocoColors.accent, RoundedCornerShape(2.dp)),
                )
            }
            Text(title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 16.5.sp)
        }
        if (onClick != null) {
            Icon(AppIcon.of("chevron_right"), contentDescription = null, tint = ChocoColors.textFaint, modifier = Modifier.size(19.dp))
        }
    }
}

/** "← Title" row used at the top of Historique / Statistiques / Paramètres /
 * Support — mirrors the RN app's components/ScreenHeaderBack.tsx. */
@Composable
fun ScreenHeaderBack(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconCircleButton(icon = AppIcon.of("arrow_back"), onClick = onBack, size = 34.dp, iconSize = 20.dp, background = androidx.compose.ui.graphics.Color(0x12FFFFFF))
        Text(title, color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 21.sp)
    }
}
