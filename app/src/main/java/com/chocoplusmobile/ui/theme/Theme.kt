package com.chocoplusmobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

private val ChocoColorScheme = darkColorScheme(
    primary = ChocoColors.accent,
    onPrimary = ChocoColors.onAccent,
    background = ChocoColors.bg,
    onBackground = ChocoColors.text,
    surface = ChocoColors.surface,
    onSurface = ChocoColors.text,
    surfaceVariant = ChocoColors.surfaceAlt,
    error = ChocoColors.danger,
)

private val ChocoTypography = Typography(
    bodyLarge = TextStyle(fontFamily = ChocoFonts.bodyRegular, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
)

/** App always renders dark, same as the RN version (there's no light theme
 * to switch to) — isSystemInDarkTheme() is intentionally ignored. */
@Composable
fun ChocoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChocoColorScheme,
        typography = ChocoTypography,
        content = content,
    )
}
