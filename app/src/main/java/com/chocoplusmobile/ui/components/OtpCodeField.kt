package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts

/**
 * A verification-code field shown as [length] individual digit boxes rather
 * than one plain text field — one real BasicTextField underneath still does
 * all the actual input handling (typing, backspace, the system paste menu),
 * it's just rendered as the boxes via `decorationBox` instead of its own
 * text. Pasting a full code fills every box at once for free: a paste is
 * just another `onValueChange` call with the whole string, which the same
 * digits-only-and-truncate filter used for normal typing already handles.
 */
@Composable
fun OtpCodeField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, length: Int = 6) {
    BasicTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter(Char::isDigit).take(length)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = modifier,
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { i ->
                    val filled = i < value.length
                    val active = i == value.length
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .background(ChocoColors.surfaceInput, RoundedCornerShape(12.dp))
                            .then(if (active) Modifier.border(1.5.dp, ChocoColors.accent, RoundedCornerShape(12.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            Text(value[i].toString(), color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 22.sp)
                        }
                    }
                }
            }
        },
    )
}
