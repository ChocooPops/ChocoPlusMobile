package com.chocoplusmobile.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.download.DownloadEntry
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.DownloadStatus
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

/**
 * The one download control used everywhere (movie detail, each episode
 * row, the Downloads screen) — its look follows the entry's state:
 *  - none: download icon, tap starts it (`onStart`);
 *  - queued/downloading: progress ring, tap asks to cancel;
 *  - completed: accent check icon, tap asks to delete the file;
 *  - failed: refresh icon, tap offers retry or removal.
 * `bordered` gives the square, outlined look of the "Ma liste" button next
 * to which it sits on the movie screen; otherwise it's a bare round icon.
 */
@Composable
fun DownloadButton(
    entry: DownloadEntry?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    bordered: Boolean = true,
) {
    var dialog by remember { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()
    val shape = if (bordered) RoundedCornerShape(10.dp) else CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(if (bordered) Modifier.border(1.dp, Color(0x40FFFFFF), shape) else Modifier)
            .clickable {
                if (entry == null) {
                    requestNotifications()
                    onStart()
                } else {
                    dialog = true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (entry?.status) {
            null -> Icon(AppIcon.of("download"), contentDescription = "Télécharger", tint = ChocoColors.text, modifier = Modifier.size(iconSize))
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                val percent = entry.progressPercent
                val ringSize = iconSize + 6.dp
                if (percent == null || entry.status == DownloadStatus.QUEUED) {
                    CircularProgressIndicator(color = ChocoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(ringSize))
                } else {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        color = ChocoColors.accent,
                        trackColor = Color(0x33FFFFFF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(ringSize),
                    )
                }
                Icon(AppIcon.of("close"), contentDescription = "Annuler le téléchargement", tint = ChocoColors.text, modifier = Modifier.size(iconSize * 0.55f))
            }
            DownloadStatus.COMPLETED -> Icon(AppIcon.of("download_done"), contentDescription = "Téléchargé", tint = ChocoColors.accent, modifier = Modifier.size(iconSize))
            DownloadStatus.FAILED -> Icon(AppIcon.of("refresh"), contentDescription = "Téléchargement échoué", tint = Color(0xFFFF6B6B), modifier = Modifier.size(iconSize))
            DownloadStatus.PAUSED -> Icon(AppIcon.of("refresh"), contentDescription = "Téléchargement en pause", tint = ChocoColors.text, modifier = Modifier.size(iconSize))
        }
    }

    if (dialog && entry != null) {
        DownloadActionDialog(entry = entry, onDismiss = { dialog = false })
    }
}

/** Confirmation for whatever tapping an existing download means in its
 * current state — cancel, delete, or retry/remove after a failure. */
@Composable
fun DownloadActionDialog(entry: DownloadEntry, onDismiss: () -> Unit) {
    val (title, text, confirm) = when (entry.status) {
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> Triple(
            "Annuler le téléchargement ?",
            entry.progressPercent?.let { "Téléchargé à $it %. La partie déjà téléchargée sera supprimée." } ?: "Le téléchargement n'a pas encore commencé.",
            "Annuler le téléchargement",
        )
        DownloadStatus.COMPLETED -> Triple(
            "Supprimer le téléchargement ?",
            "La vidéo ne sera plus disponible hors ligne." + (entry.totalBytes.takeIf { it > 0 }?.let { " ${formatBytes(it)} seront libérés." } ?: ""),
            "Supprimer",
        )
        DownloadStatus.FAILED -> Triple(
            "Téléchargement échoué",
            entry.error ?: "Une erreur est survenue.",
            "Supprimer",
        )
        DownloadStatus.PAUSED -> Triple(
            "Téléchargement en pause",
            entry.progressPercent?.let { "Interrompu à $it %. Il reprendra là où il s'est arrêté." } ?: "Le téléchargement a été interrompu.",
            "Supprimer",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ChocoColors.surfaceAlt,
        title = { Text(title, color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 17.sp) },
        text = { Text(text, color = Color(0xB3FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = { DownloadManager.remove(entry.key); onDismiss() }) {
                Text(confirm, color = Color(0xFFFF6B6B), fontFamily = ChocoFonts.bodyBold)
            }
        },
        dismissButton = {
            if (entry.status == DownloadStatus.FAILED || entry.status == DownloadStatus.PAUSED) {
                TextButton(onClick = { DownloadManager.retry(entry.key); onDismiss() }) {
                    Text(if (entry.status == DownloadStatus.PAUSED) "Reprendre" else "Réessayer", color = ChocoColors.accent, fontFamily = ChocoFonts.bodyBold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Fermer", color = ChocoColors.text, fontFamily = ChocoFonts.bodyBold)
                }
            }
        },
    )
}

/** Android 13+ hides the download progress notification (DownloadService)
 * unless the user granted POST_NOTIFICATIONS — asked for the first time a
 * download is started, not at app launch. Downloads work either way. */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f Go".format(mb / 1024) else "%.0f Mo".format(mb)
}
