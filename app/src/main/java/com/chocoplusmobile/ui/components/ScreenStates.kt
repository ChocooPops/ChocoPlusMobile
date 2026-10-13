package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

/** Mirrors the RN app's components/ScreenState.tsx (Loading/Error) plus
 * EmptyState.tsx — kept together since they're all tiny "centered column"
 * states used the same way across every screen. */

@Composable
fun LoadingState(modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = if (compact) 30.dp else 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = ChocoColors.accent)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcon.of("wifi_off"), contentDescription = null, tint = ChocoColors.textMuted, modifier = Modifier.size(36.dp))
        Text("Connexion au serveur impossible", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 15.sp)
        Text(message, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        PrimaryButton(label = "Réessayer", onClick = onRetry, height = 42.dp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onPressCta: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ChocoColors.accentDim, modifier = Modifier.size(44.dp))
        Text(title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 17.sp)
        Text(description, color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        if (ctaLabel != null && onPressCta != null) {
            PrimaryButton(label = ctaLabel, onClick = onPressCta, height = 42.dp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The outcome of a single in-flight action shown via ActionStatusOverlay
 * below — Loading while the request is in flight, Done once the server has
 * actually answered, carrying its own message (as returned — can be
 * several lines) and whether it read as a success. */
sealed class ActionStatus {
    object Loading : ActionStatus()
    data class Done(val message: String, val success: Boolean) : ActionStatus()
}

/**
 * A small blocking popup — dimmed scrim behind a centered card — for a
 * single API call whose result changes what's on screen (a delete, a role
 * change…), as opposed to LoadingState above, which replaces a whole
 * screen's content while it first loads. Shows a spinner while `status` is
 * Loading, then morphs in place into the server's own response message
 * once it's Done, rather than just vanishing — the caller only finds out
 * what actually happened by reading it there and dismissing it (OK button,
 * tap outside, or back). Uses a real Dialog (its own window) so it sits
 * above everything already on screen, including a ChocoBottomSheet or
 * AlertDialog. The Loading phase itself isn't dismissible — it tracks a
 * request in flight, not something to cancel by tapping away.
 */
@Composable
fun ActionStatusOverlay(status: ActionStatus?, onDismiss: () -> Unit) {
    if (status == null) return
    val dismissible = status is ActionStatus.Done
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 300.dp)
                    .background(ChocoColors.surfaceAlt, RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (status) {
                    is ActionStatus.Loading -> {
                        CircularProgressIndicator(color = ChocoColors.accent)
                        Text("Chargement…", color = ChocoColors.text, fontFamily = ChocoFonts.bodyMedium, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                    is ActionStatus.Done -> {
                        Icon(
                            AppIcon.of(if (status.success) "task_alt" else "error_outline"),
                            contentDescription = null,
                            tint = if (status.success) ChocoColors.accent else ChocoColors.danger,
                            modifier = Modifier.size(30.dp),
                        )
                        Text(
                            status.message.trim(),
                            color = ChocoColors.text,
                            fontFamily = ChocoFonts.bodyMedium,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                        )
                        PrimaryButton(label = "OK", onClick = onDismiss, height = 40.dp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
