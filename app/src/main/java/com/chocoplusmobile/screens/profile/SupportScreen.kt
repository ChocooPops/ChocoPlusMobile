@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chocoplusmobile.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.SubmitSupportPayload
import com.chocoplusmobile.api.Support
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.components.ScreenHeaderBack
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text

/** Mirrors the RN app's screens/Profile/SupportScreen.tsx. */
@Composable
fun SupportScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    var topics by remember { mutableStateOf(listOf<Support>()) }
    var topicsLoading by remember { mutableStateOf(true) }
    var topicsError by remember { mutableStateOf<String?>(null) }
    var topicsRetry by remember { mutableStateOf(0) }

    var subject by remember { mutableStateOf("") }
    var area by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(topicsRetry) {
        topicsLoading = true
        topicsError = null
        try {
            topics = NetworkModule.safeCall { NetworkModule.api.getSupportTopics() }
        } catch (e: Exception) {
            topicsError = e.message ?: Tr.NETWORK_ERROR.text()
        } finally {
            topicsLoading = false
        }
    }

    val ready = subject.trim().isNotEmpty() && area != null && description.trim().isNotEmpty()

    fun reset() {
        subject = ""; area = null; description = ""; sent = false; submitError = null
    }

    fun submit() {
        if (!ready || sending) return
        sending = true
        submitError = null
        scope.launch {
            try {
                // The success card below used to show unconditionally
                // whenever this call didn't throw — but the endpoint can
                // come back 200 with state:false (e.g. a rejected report),
                // which was silently treated as "sent" too.
                val result = NetworkModule.safeCall { NetworkModule.api.submitSupport(SubmitSupportPayload(subject.trim(), listOf(area!!), description.trim())) }
                if (result.state) {
                    sent = true
                } else {
                    submitError = result.message.ifBlank { Tr.SUPPORT_SEND_FAILED.text() }
                }
            } catch (e: Exception) {
                submitError = e.message ?: Tr.SUPPORT_SEND_FAILED.text()
            } finally {
                sending = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(top = 50.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
        ScreenHeaderBack(title = t(Tr.SUPPORT_TITLE), onBack = { navController.popBackStack() })
        Text(
            t(Tr.SUPPORT_INTRO),
            color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, lineHeight = 19.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        if (sent) {
            Row(
                modifier = Modifier.fillMaxWidth().background(ChocoColors.accentSoft, RoundedCornerShape(14.dp)).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcon.of("task_alt"), contentDescription = null, tint = ChocoColors.accent, modifier = androidx.compose.ui.Modifier)
                Column(modifier = Modifier.weight(1f)) {
                    Text(t(Tr.SUPPORT_SENT_TITLE), color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Text(t(Tr.SUPPORT_SENT_DESC), color = ChocoColors.textSecondary, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp, lineHeight = 19.sp)
                    PrimaryButton(label = t(Tr.SUPPORT_NEW_REPORT), onClick = ::reset, height = 36.dp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        } else {
            SupportFieldLabel(t(Tr.SUPPORT_SUBJECT), required = true)
            SupportField(subject, { subject = it }, t(Tr.SUPPORT_SUBJECT_HINT))

            SupportFieldLabel(t(Tr.SUPPORT_AREA), required = true, topPadding = 18.dp)
            when {
                topicsLoading -> LoadingState(compact = true)
                topicsError != null -> ErrorState(message = topicsError!!, onRetry = { topicsRetry++ })
                else -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        topics.forEach { t ->
                            val active = area == t.subject
                            Row(
                                modifier = Modifier
                                    .background(if (active) ChocoColors.accent else Color.Transparent, RoundedCornerShape(99.dp))
                                    .clickable { area = t.subject }
                                    .padding(horizontal = 13.dp, vertical = 8.dp),
                            ) {
                                Text(t.subject, color = if (active) ChocoColors.onAccent else ChocoColors.textTertiary, fontFamily = ChocoFonts.bodyBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            SupportFieldLabel(t(Tr.SUPPORT_DESCRIPTION), required = true, topPadding = 18.dp)
            TextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text(t(Tr.SUPPORT_DESCRIPTION_HINT), color = Color(0x59FFFFFF)) },
                textStyle = TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 14.sp, lineHeight = 20.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().background(ChocoColors.surfaceInput, RoundedCornerShape(11.dp)).height(110.dp),
            )

            if (submitError != null) Text(submitError!!, color = Color(0xFFFF9C9C), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))

            if (ready) {
                PrimaryButton(label = t(Tr.SUPPORT_SEND), onClick = ::submit, loading = sending, height = 48.dp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(48.dp).background(Color(0x0FFFFFFF), RoundedCornerShape(12.dp)),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(t(Tr.SUPPORT_ALL_REQUIRED), color = ChocoColors.textDisabled, fontFamily = ChocoFonts.bodyBold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 13.dp))
                }
            }
        }
    }
}

@Composable
private fun SupportFieldLabel(text: String, required: Boolean = false, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(modifier = Modifier.padding(top = topPadding, bottom = 7.dp)) {
        Text(text, color = ChocoColors.textTertiary, fontFamily = ChocoFonts.bodyBold, fontSize = 11.5.sp)
        if (required) Text(" *", color = ChocoColors.accent, fontFamily = ChocoFonts.bodyBold, fontSize = 11.5.sp)
    }
}

@Composable
private fun SupportField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0x59FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 14.sp) },
        singleLine = true,
        textStyle = TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 14.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().height(46.dp).background(ChocoColors.surfaceInput, RoundedCornerShape(11.dp)),
    )
}
