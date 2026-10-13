package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.ui.icons.TabIconName
import com.chocoplusmobile.ui.icons.TabIcons
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

/** How much bottom clearance a scrollable screen needs to reserve so its
 * last row doesn't end up hidden behind the floating bar — mirrors the RN
 * app's own `insets.bottom + 100` footer-spacer convention, since
 * BottomNavBar floats as an overlay here too (see MainGraph's docstring)
 * rather than reserving its own layout space. */
val TAB_BAR_CLEARANCE = 70.dp

data class BottomNavTab(val route: String, val icon: TabIconName, val label: String)

val BOTTOM_NAV_TABS = listOf(
    BottomNavTab("home", TabIconName.HOME, "Accueil"),
    BottomNavTab("films", TabIconName.MOVIES, "Films"),
    BottomNavTab("series", TabIconName.SERIES, "Séries"),
    BottomNavTab("catalogue", TabIconName.CATALOGUE, "Catalogue"),
    BottomNavTab("mylist", TabIconName.MYLIST, "Ma liste"),
)

/** Mirrors the RN app's components/CustomTabBar.tsx — Search deliberately
 * has no button here (reached only via the TopBar magnifying-glass icon),
 * same as the RN version. Profile is likewise reached only via the TopBar
 * avatar, a deliberate deviation from the RN version (which does tab it),
 * per user request. */
@Composable
fun BottomNavBar(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF141414))))
            .padding(top = 10.dp)
            .padding(WindowInsets.navigationBars.asPaddingValues()),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            BOTTOM_NAV_TABS.forEach { tab ->
                val focused = tab.route == currentRoute
                val tint = if (focused) ChocoColors.accent else ChocoColors.textMuted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { if (!focused) onSelect(tab.route) }
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = TabIcons.get(tab.icon, tint),
                        contentDescription = tab.label,
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        color = tint,
                        fontFamily = if (focused) ChocoFonts.bodyBold else ChocoFonts.bodyMedium,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
