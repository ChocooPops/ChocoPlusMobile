package com.chocoplusmobile.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The desktop mockup uses Google "Material Symbols" glyph names (snake_case,
 * e.g. "play_arrow", "grid_view"); the RN app kept that vocabulary
 * throughout and translated to react-native-vector-icons at the very edge
 * (Icon.tsx). This is that same translation layer, targeting
 * material-icons-extended instead — every screen below still just says
 * `AppIcon.of("play_arrow")`, kept snake_case for a 1:1 diff against the RN
 * source rather than renaming every call site to PascalCase.
 */
object AppIcon {
    private val map: Map<String, ImageVector> by lazy {
        mapOf(
            "mail" to Icons.Filled.Email,
            "lock" to Icons.Filled.Lock,
            "error_outline" to Icons.Filled.ErrorOutline,
            "search" to Icons.Filled.Search,
            "cancel" to Icons.Filled.Cancel,
            "chevron_right" to Icons.Filled.ChevronRight,
            "chevron_left" to Icons.Filled.ChevronLeft,
            "history" to Icons.Filled.History,
            "tune" to Icons.Filled.Tune,
            "close" to Icons.Filled.Close,
            "play_circle" to Icons.Filled.PlayCircle,
            "stop_circle" to Icons.Filled.StopCircle,
            "campaign" to Icons.Filled.Campaign,
            "calendar_month" to Icons.Filled.CalendarMonth,
            "play_arrow" to PlayArrowVector,
            "pause" to Icons.Filled.Pause,
            "mark_email_unread" to Icons.Filled.MarkEmailUnread,
            "task_alt" to Icons.Filled.TaskAlt,
            "menu_book" to Icons.Filled.MenuBook,
            "keyboard_arrow_down" to Icons.Filled.KeyboardArrowDown,
            "subtitles" to Icons.Filled.Subtitles,
            "history_toggle_off" to Icons.Filled.HistoryToggleOff,
            "speed" to Icons.Filled.Speed,
            "cast" to Icons.Filled.Cast,
            "aspect_ratio" to Icons.Filled.AspectRatio,
            "check" to Icons.Filled.Check,
            "arrow_back" to Icons.AutoMirrored.Filled.ArrowBack,
            "sync_alt" to Icons.Filled.SyncAlt,
            "verified_user" to Icons.Filled.VerifiedUser,
            "image" to Icons.Filled.Image,
            "title" to Icons.Filled.Title,
            "translate" to Icons.Filled.Translate,
            "crop_portrait" to Icons.Filled.CropPortrait,
            "crop_landscape" to Icons.Filled.CropLandscape,
            "bookmark_add" to Icons.Filled.BookmarkAdd,
            "wifi_off" to Icons.Filled.WifiOff,
            "add" to Icons.Filled.Add,
            "grid_view" to Icons.Filled.GridView,
            "view_agenda" to Icons.Filled.ViewAgenda,
            "volume_off" to Icons.Filled.VolumeOff,
            "volume_up" to Icons.Filled.VolumeUp,
            "expand_less" to Icons.Filled.ExpandLess,
            "expand_more" to Icons.Filled.ExpandMore,
            "replay_10" to Icons.Filled.Replay10,
            "forward_10" to Icons.Filled.Forward10,
            "bar_chart" to Icons.Filled.BarChart,
            "manage_accounts" to Icons.Filled.ManageAccounts,
            "bug_report" to Icons.Filled.BugReport,
            "settings" to Icons.Filled.Settings,
            "edit" to Icons.Filled.Edit,
            "info" to Icons.Filled.Info,
            "visibility" to Icons.Filled.Visibility,
            "visibility_off" to Icons.Filled.VisibilityOff,
            "memory" to Icons.Filled.Memory,
            "bolt" to Icons.Filled.Bolt,
            "video_library" to Icons.Filled.VideoLibrary,
            "delete_outline" to Icons.Filled.DeleteOutline,
            "download" to Icons.Filled.Download,
            "download_done" to Icons.Filled.DownloadDone,
            "download_for_offline" to Icons.Filled.DownloadForOffline,
            "refresh" to Icons.Filled.Refresh,
        )
    }

    fun of(name: String): ImageVector = map[name] ?: Icons.Filled.Info
}
