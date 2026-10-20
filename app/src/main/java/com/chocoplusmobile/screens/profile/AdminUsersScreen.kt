@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.Role
import com.chocoplusmobile.api.UpdateUserRolePayload
import com.chocoplusmobile.api.User
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ActionStatus
import com.chocoplusmobile.ui.components.ActionStatusOverlay
import com.chocoplusmobile.ui.components.Avatar
import com.chocoplusmobile.ui.components.ChocoBottomSheet
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.ScreenHeaderBack
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text
import com.chocoplusmobile.i18n.tCount

/** background/foreground pair for a role badge — a soft tint of the same
 * solid color behind text drawn in that solid color, same recipe as
 * ChocoColors.accentSoft/accent elsewhere in the app, just per role instead
 * of a single fixed color. */
private fun roleColors(role: Role): Pair<Color, Color> {
    val solid = when (role) {
        Role.ADMIN -> ChocoColors.accent
        Role.USER -> Color(0xFF5B9FE3) // blue
        Role.FAMILY -> Color(0xFF5FD68C) // green
        Role.SUSPENDED -> ChocoColors.danger // red
        Role.NOT_ACTIVATE -> Color(0xFF9AA0A6) // grey
    }
    return solid.copy(alpha = 0.16f) to solid
}

/**
 * Admin-only user management — matches user.controller.ts's admin routes
 * (all/:id under AdminUserGuard server-side; this screen is only reachable
 * at all when the current user's own role is ADMIN, see ProfileScreen).
 */
@Composable
fun AdminUsersScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf(listOf<User>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }
    var actionStatus by remember { mutableStateOf<ActionStatus?>(null) }

    var roleSheetUser by remember { mutableStateOf<User?>(null) }
    var deleteTarget by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(retryTick) {
        loading = true
        error = null
        try {
            users = NetworkModule.safeCall { NetworkModule.api.getAllUsers() }
        } catch (e: Exception) {
            error = e.message ?: Tr.NETWORK_ERROR.text()
        } finally {
            loading = false
        }
    }

    fun changeRole(user: User, role: Role) {
        roleSheetUser = null
        actionStatus = ActionStatus.Loading
        scope.launch {
            actionStatus = try {
                val result = NetworkModule.safeCall { NetworkModule.api.updateUserRoleByAdmin(user.id, UpdateUserRolePayload(role)) }
                if (result.state) users = users.map { if (it.id == user.id) it.copy(role = role) else it }
                ActionStatus.Done(result.message.ifBlank { Tr.ADMIN_ROLE_UPDATED.text() }, result.state)
            } catch (e: Exception) {
                ActionStatus.Done(e.message ?: Tr.ADMIN_ROLE_FAILED.text(), success = false)
            }
        }
    }

    fun deleteUser(user: User) {
        deleteTarget = null
        actionStatus = ActionStatus.Loading
        scope.launch {
            actionStatus = try {
                val result = NetworkModule.safeCall { NetworkModule.api.deleteUserByAdmin(user.id) }
                if (result.state) users = users.filter { it.id != user.id }
                ActionStatus.Done(result.message.ifBlank { Tr.ADMIN_USER_DELETED.text() }, result.state)
            } catch (e: Exception) {
                ActionStatus.Done(e.message ?: Tr.ADMIN_DELETE_FAILED.text(), success = false)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(top = 50.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
        ScreenHeaderBack(title = t(Tr.PROFILE_ADMIN), onBack = { navController.popBackStack() })
        Text(
            tCount(Tr.ADMIN_ACCOUNTS_ONE, Tr.ADMIN_ACCOUNTS_MANY, users.size),
            color = ChocoColors.textMuted,
            fontFamily = ChocoFonts.bodyRegular,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )

        when {
            loading -> LoadingState()
            error != null -> ErrorState(message = error!!, onRetry = { retryTick++ })
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth().background(ChocoColors.surfaceAlt, RoundedCornerShape(14.dp)),
                ) {
                    users.forEachIndexed { idx, u ->
                        AdminUserRow(
                            user = u,
                            onClick = { roleSheetUser = u },
                            onDelete = { deleteTarget = u },
                        )
                        if (idx < users.lastIndex) {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp).background(ChocoColors.divider).height(1.dp))
                        }
                    }
                }
            }
        }
    }

    ChocoBottomSheet(visible = roleSheetUser != null, onClose = { roleSheetUser = null }) {
        val u = roleSheetUser
        if (u != null) {
            Text(u.pseudo, color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 18.sp)
            Text(u.email, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 18.dp))
            Text(t(Tr.ADMIN_ROLE).uppercase(), color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Role.entries.forEach { r ->
                    RoleChip(role = r, active = u.role == r, onClick = { if (u.role != r) changeRole(u, r) })
                }
            }
        }
    }

    if (deleteTarget != null) {
        val u = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t(Tr.ADMIN_DELETE_CONFIRM, u.pseudo)) },
            text = { Text(t(Tr.ADMIN_DELETE_DESC)) },
            confirmButton = { TextButton(onClick = { deleteUser(u) }) { Text(t(Tr.DELETE), color = ChocoColors.danger) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(t(Tr.CANCEL)) } },
        )
    }

    ActionStatusOverlay(status = actionStatus, onDismiss = { actionStatus = null })
}

/** Same pill shape/behaviour as ChocoChip, but coloured per role
 * (roleColors) instead of the app's single generic accent — active state
 * fills solid instead of just tinting, so the selected role reads clearly
 * even across 5 different hues. */
@Composable
private fun RoleChip(role: Role, active: Boolean, onClick: () -> Unit) {
    val (bg, fg) = roleColors(role)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) fg else bg)
            .border(1.dp, fg, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            role.name,
            color = if (active) ChocoColors.onAccent else fg,
            fontFamily = if (active) ChocoFonts.bodyBold else ChocoFonts.bodyMedium,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun AdminUserRow(user: User, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Avatar(initial = user.firstName.firstOrNull()?.uppercase() ?: "?", uri = user.profilPhoto, size = 44.dp, radius = 22.dp)
        Column(modifier = Modifier.weight(1f)) {
            val displayName = "${user.firstName} ${user.lastName}".trim().ifEmpty { user.pseudo }
            Text(displayName, color = ChocoColors.text, fontFamily = ChocoFonts.bodySemiBold, fontSize = 14.sp, maxLines = 1)
            Text(user.email, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            val (badgeBg, badgeFg) = roleColors(user.role)
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(badgeBg, RoundedCornerShape(99.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(user.role.name, color = badgeFg, fontFamily = ChocoFonts.bodyBold, fontSize = 9.5.sp, letterSpacing = 0.4.sp)
            }
        }
        Icon(
            AppIcon.of("delete_outline"),
            contentDescription = t(Tr.DELETE),
            tint = ChocoColors.danger,
            modifier = Modifier.size(22.dp).clickable(onClick = onDelete),
        )
    }
}
