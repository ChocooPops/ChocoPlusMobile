package com.chocoplusmobile.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chocoplusmobile.R

/**
 * Font families bundled as static TTF instances — back to mirroring the RN
 * app's theme/typography.ts (Outfit for headings, Plus Jakarta Sans for
 * body), per user request reverting the earlier Arial swap.
 *
 * Each `Font(resId, weight = ...)` now tags its *real* physical weight
 * (previously these didn't bother, since the weight distinction was made
 * entirely by which named property/file a screen picked — fine until
 * Theme.kt's ChocoTypography started requesting an ambient FontWeight.Bold
 * app-wide for "tout en gras"). Tagging the true weight lets Compose tell
 * "this file is already bold, render as-is" apart from "this file is
 * regular/medium, synthesize bold on top" — without it, headingBold/
 * headingExtraBold/bodyBold would each get a synthetic bold layered onto
 * their already-bold glyphs (double-bolded, muddy) whenever the ambient
 * default (or an explicit request) asks for Bold.
 *
 * `knewave` is the one deliberate exception — used only for the "Bienvenue"
 * headline on WelcomeBackScreen, not folded into the heading/body scale
 * above.
 */
object ChocoFonts {
    val headingRegular = FontFamily(Font(R.font.outfit_regular, weight = FontWeight.Normal))
    val headingMedium = FontFamily(Font(R.font.outfit_medium, weight = FontWeight.Medium))
    val headingSemiBold = FontFamily(Font(R.font.outfit_semibold, weight = FontWeight.SemiBold))
    val headingBold = FontFamily(Font(R.font.outfit_bold, weight = FontWeight.Bold))
    val headingExtraBold = FontFamily(Font(R.font.outfit_extrabold, weight = FontWeight.ExtraBold))

    val bodyRegular = FontFamily(Font(R.font.jakarta_regular, weight = FontWeight.Normal))
    val bodyMedium = FontFamily(Font(R.font.jakarta_medium, weight = FontWeight.Medium))
    val bodySemiBold = FontFamily(Font(R.font.jakarta_semibold, weight = FontWeight.SemiBold))
    val bodyBold = FontFamily(Font(R.font.jakarta_bold, weight = FontWeight.Bold))

    val knewave = FontFamily(Font(R.font.knewave))
}
