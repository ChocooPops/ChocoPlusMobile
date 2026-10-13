package com.chocoplusmobile.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Role
import com.chocoplusmobile.download.canDownload
import com.chocoplusmobile.api.WatchTimeStats
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.Avatar
import com.chocoplusmobile.ui.components.ButtonVariant
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.components.TopBar
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

private data class MenuItem(val icon: String, val label: String, val desc: String, val route: String)

private val MENU = listOf(
    MenuItem("history", "Historique", "Tes visionnages, filtrables par date", "history"),
    MenuItem("bar_chart", "Statistiques", "Heures, catégories, rythme", "stats"),
    MenuItem("manage_accounts", "Gérer le profil", "Pseudo, photo, mot de passe", "editProfile"),
    MenuItem("menu_book", "Documentation", "Guides et FAQ intégrés", "documentation"),
    MenuItem("bug_report", "Support", "Signaler un bug à l'équipe", "support"),
    MenuItem("settings", "Paramètres", "Affichage, démarrage, lecture", "settings"),
)

// Only shown to ADMIN accounts — appended to MENU below rather than baked
// in, since every other entry is unconditional.
private val ADMIN_MENU_ITEM = MenuItem("verified_user", "Administration", "Gérer les comptes utilisateurs", "adminUsers")

// ADMIN/FAMILY only, same as every other download entry point.
private val DOWNLOADS_MENU_ITEM = MenuItem("download_for_offline", "Téléchargements", "Films et épisodes disponibles hors ligne", "downloads")

/** Mirrors the RN app's screens/Profile/ProfileScreen.tsx. */
@Composable
fun ProfileScreen() {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    val user by appViewModel.user.collectAsState()
    val myListIds by appViewModel.myListIds.collectAsState()
    var watchTime by remember { mutableStateOf<WatchTimeStats?>(null) }

    LaunchedEffect(Unit) {
        try {
            watchTime = NetworkModule.safeCall { NetworkModule.api.getWatchTime() }
        } catch (e: Exception) { /* ignore */ }
    }

    val menu = remember(user?.role) {
        val downloads = if (user?.role.canDownload()) listOf(DOWNLOADS_MENU_ITEM) else emptyList()
        val admin = if (user?.role == Role.ADMIN) listOf(ADMIN_MENU_ITEM) else emptyList()
        downloads + MENU + admin
    }

    val kpis = listOf(
        Triple(watchTime?.total?.contentCount?.toString() ?: "—", "titres vus", null as ImageVector?),
        Triple(watchTime?.let { "${it.total.totalHours} h" } ?: "—", "de visionnage", null),
        Triple(myListIds.size.toString(), "dans ma liste", null),
    )

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
        TopBar(onSearch = { navController.navigate("search") }, onAvatar = {}, avatarInitial = user?.firstName?.firstOrNull()?.uppercase() ?: "?", avatarUri = user?.profilPhoto)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(initial = user?.firstName?.firstOrNull()?.uppercase() ?: "?", uri = user?.profilPhoto, size = 74.dp, radius = 8.dp, editable = true, onClick = { navController.navigate("editProfile") })
                Column(modifier = Modifier.weight(1f)) {
                    Text(user?.firstName ?: "", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 21.sp, letterSpacing = (-0.4).sp)
                    Text(user?.email ?: "", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Row(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .background(ChocoColors.accentSoft, RoundedCornerShape(99.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(AppIcon.of("verified_user"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(13.dp))
                        Text(user?.role?.name ?: "", color = ChocoColors.accent, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                kpis.forEach { (value, label, _) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(ChocoColors.surfaceAlt2, RoundedCornerShape(12.dp))
                            .padding(13.dp),
                    ) {
                        Text(value, color = ChocoColors.accent, fontFamily = ChocoFonts.headingExtraBold, fontSize = 22.sp, letterSpacing = (-0.5).sp)
                        Text(label, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 10.5.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .background(ChocoColors.surfaceAlt, RoundedCornerShape(14.dp)),
            ) {
                menu.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(item.route) }
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Icon(AppIcon.of(item.icon), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(21.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.label, color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 14.sp)
                            Text(item.desc, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Icon(AppIcon.of("chevron_right"), contentDescription = null, tint = ChocoColors.textDisabled, modifier = Modifier.size(20.dp))
                    }
                    if (idx < menu.lastIndex) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp).background(ChocoColors.divider).height(1.dp))
                    }
                }
            }

            PrimaryButton(label = "Se déconnecter", variant = ButtonVariant.OUTLINE, onClick = { appViewModel.logout() }, height = 46.dp, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            Text("ChocoPlus", color = ChocoColors.textDisabled, fontFamily = ChocoFonts.bodyRegular, fontSize = 10.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
