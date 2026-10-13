package com.chocoplusmobile.ui.theme

import androidx.compose.ui.graphics.Color

/** ChocoPlus design tokens — mirrors the RN app's theme/colors.ts, itself
 * mirroring the #141414 / #FFD301 palette from the desktop app. The app has
 * no light theme (same as the RN version), so these are just top-level
 * constants rather than a light/dark pair. */
object ChocoColors {
    val bg = Color(0xFF141414)
    val surface = Color(0xFF141414)
    val surfaceAlt = Color(0xFF1A1A1A)
    val surfaceAlt2 = Color(0xFF1C1C1C)
    val surfaceInput = Color(0xFF1F1F1F)
    val card = Color(0xFF232323)

    val accent = Color(0xFFFFD301)
    val accentSoft = Color(0x1FFFD301)
    val accentBorder = Color(0x59FFD301)
    val accentDim = Color(0x73FFD301)

    val border = Color(0x12FFFFFF)
    val borderStrong = Color(0x24FFFFFF)
    val borderInput = Color(0x1AFFFFFF)
    val divider = Color(0x0FFFFFFF)

    val text = Color(0xFFFFFFFF)
    val textSecondary = Color(0xB8FFFFFF)
    val textTertiary = Color(0x8CFFFFFF)
    val textMuted = Color(0x6BFFFFFF)
    val textFaint = Color(0x4DFFFFFF)
    val textDisabled = Color(0x38FFFFFF)

    val onAccent = Color(0xFF141414)

    val danger = Color(0xFFFF6B6B)
    val dangerSoft = Color(0x1FFF6B6B)

    val overlayScrim = Color(0x80000000)
    val sheetOverlay = Color(0x99000000)

    val posterEmptyBorder = Color(0x29FFFFFF)
    val posterEmptyLabelBg = Color(0xCC000000)
    val posterEmptyLabelText = Color(0xFFFFFFFF)
    val posterShadow = Color(0xFF000000)
}

object ChocoRadii {
    val sm = 7
    val md = 5
    val lg = 12
    val xl = 14
    val pill = 999
}

object ChocoSpacing {
    val xs = 4
    val sm = 8
    val md = 12
    val lg = 16
    val xl = 20
    val xxl = 28
}
