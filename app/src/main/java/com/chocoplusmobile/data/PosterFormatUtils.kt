package com.chocoplusmobile.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector
import com.chocoplusmobile.state.PosterAlignment
import com.chocoplusmobile.state.PosterFormat

/** Mirrors the RN app's utils/posterFormat.ts. */
object PosterFormatUtils {

    /** Action-oriented convention every format-adjacent icon in this app
     * follows: shows the state the user would switch *to*, not the current
     * one. */
    fun nextAlignmentIcon(current: PosterAlignment): ImageVector =
        if (current == PosterAlignment.SINGLE) Icons.Filled.GridView else Icons.Filled.ViewAgenda

    /** How many poster tiles sit on one line for a given shape+alignment.
     * SINGLE is always one per line; GRID packs 3 vertical posters (narrow)
     * or 2 horizontal ones (wide). */
    fun gridColumns(format: PosterFormat, alignment: PosterAlignment): Int {
        if (alignment == PosterAlignment.SINGLE) return 1
        return if (format == PosterFormat.V) 3 else 2
    }
}
