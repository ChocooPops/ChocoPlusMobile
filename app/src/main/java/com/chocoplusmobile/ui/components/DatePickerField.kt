@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.chocoplusmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t

private val ISO_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

private fun isoToDisplay(iso: String): String =
    try {
        LocalDate.parse(iso.trim(), ISO_FORMAT).format(DISPLAY_FORMAT)
    } catch (e: Exception) {
        ""
    }

/** Material3's DatePicker works in UTC-midnight epoch millis regardless of
 * the device's own timezone — converting through a local timezone here would
 * shift the selected day by one for roughly half the world, so both
 * directions pin to UTC explicitly instead of using the platform default. */
private fun isoToUtcMillis(iso: String): Long? =
    try {
        LocalDate.parse(iso.trim(), ISO_FORMAT).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(ISO_FORMAT)

/**
 * A date-of-birth field that can't produce a malformed value — the actual
 * problem with the plain text field it replaces (SignupScreen/
 * EditProfileScreen both used to ask people to type "AAAA-MM-JJ" by hand,
 * which nothing stopped them from getting wrong). Shown to the user in the
 * familiar JJ/MM/AAAA form; [onIsoValueChange] always receives a real
 * "yyyy-MM-dd" string, exactly what the backend's DATE column expects.
 */
@Composable
fun DatePickerField(
    isoValue: String,
    onIsoValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "JJ/MM/AAAA",
    height: Dp = 48.dp,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val display = remember(isoValue) { isoToDisplay(isoValue) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(ChocoColors.surfaceInput, RoundedCornerShape(11.dp))
            .clickable { pickerOpen = true }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            display.ifEmpty { placeholder },
            color = if (display.isEmpty()) Color(0x59FFFFFF) else ChocoColors.text,
            fontFamily = ChocoFonts.bodyRegular,
            fontSize = 14.5.sp,
        )
        Icon(AppIcon.of("calendar_month"), contentDescription = null, tint = ChocoColors.textMuted, modifier = Modifier.size(19.dp))
    }

    if (pickerOpen) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = isoToUtcMillis(isoValue),
            // Nobody signing up was born in the future.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onIsoValueChange(utcMillisToIso(it)) }
                    pickerOpen = false
                }) { Text(t(Tr.OK)) }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text(t(Tr.CANCEL)) } },
        ) {
            DatePicker(state = state)
        }
    }
}
