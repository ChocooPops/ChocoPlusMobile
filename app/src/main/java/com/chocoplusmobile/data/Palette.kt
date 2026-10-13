package com.chocoplusmobile.data

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Deterministic "poster" gradient derived from a title — mirrors the RN
 * app's utils/palette.ts (which itself mirrors the desktop mockup's
 * hash → hue trick). */
object Palette {
    private fun hashHue(str: String): Int {
        var h = 0
        for (c in str) h = (h * 31 + c.code) % 360
        return if (h < 0) h + 360 else h
    }

    private fun hslToColor(h: Int, s: Int, l: Int): Color {
        val sat = s / 100f
        val lig = l / 100f
        val k = { n: Int -> (n + h / 30) % 12 }
        val a = sat * min(lig, 1 - lig)
        val f = { n: Int -> lig - a * max(-1f, min(k(n) - 3f, min(9 - k(n).toFloat(), 1f))) }
        fun channel(n: Int) = (f(n).coerceIn(0f, 1f) * 255).roundToInt()
        return Color(channel(0), channel(8), channel(4))
    }

    /** Three-stop gradient (light → mid → near-black) used behind poster titles. */
    fun posterGradient(title: String): Triple<Color, Color, Color> {
        val h1 = hashHue(title.ifEmpty { "?" })
        val h2 = (h1 + 42) % 360
        return Triple(hslToColor(h1, 55, 46), hslToColor(h2, 48, 27), hslToColor(h2, 35, 11))
    }

    /** Small stable avatar/cast-bubble color, single hue. */
    fun accentTint(seed: String): Color = hslToColor(hashHue(seed.ifEmpty { "?" }), 45, 32)
}
