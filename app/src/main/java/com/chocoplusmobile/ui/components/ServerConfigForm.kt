package com.chocoplusmobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chocoplusmobile.R
import com.chocoplusmobile.api.ServerConfig
import com.chocoplusmobile.api.ServerConfigHolder
import com.chocoplusmobile.api.ServerConnectionTester
import com.chocoplusmobile.api.ServerTestResult
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch

private val SuccessColor = Color(0xFF6BD68A)

/**
 * "Communication avec le serveur": backend URL + secret header pair, with a
 * connection test (bare URL, header only — no endpoint) and a save that
 * restarts the app on the new values (see ServerConfigHolder.save).
 */
@Composable
fun ServerConfigForm(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { ServerConfigHolder.current }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var headerName by remember { mutableStateOf(initial?.headerName.orEmpty()) }
    var headerValue by remember { mutableStateOf(initial?.headerValue.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ServerTestResult?>(null) }

    val config = ServerConfig.of(url, headerName, headerValue)
    val error = config.validationError()
    // Only nag once something's typed — an empty first-launch form is fine.
    val shownError = error.takeIf { url.isNotBlank() || headerName.isNotBlank() || headerValue.isNotEmpty() }

    // Any edit invalidates the previous test outcome.
    fun edit(set: () -> Unit) { set(); result = null }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ChocoColors.surfaceAlt)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text("Communication avec le serveur", color = ChocoColors.text, fontFamily = ChocoFonts.headingBold, fontSize = 20.sp)

        ConfigField(
            label = "Url principale du serveur :",
            value = url,
            onValueChange = { v -> edit { url = v } },
            placeholder = "https://api.exemple.com",
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.padding(top = 24.dp),
        )
        ConfigField(
            label = "Nom du header de sécurité :",
            value = headerName,
            onValueChange = { v -> edit { headerName = v } },
            secret = true,
            modifier = Modifier.padding(top = 18.dp),
        )
        ConfigField(
            label = "Clé du header de sécurité :",
            value = headerValue,
            onValueChange = { v -> edit { headerValue = v } },
            secret = true,
            modifier = Modifier.padding(top = 18.dp),
        )

        val message = result?.message ?: shownError
        if (message != null) {
            Text(
                message,
                color = if (result?.success == true) SuccessColor else Color(0xFFFF9C9C),
                fontFamily = ChocoFonts.bodyMedium,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton(
                label = "Tester",
                variant = ButtonVariant.OUTLINE,
                enabled = error == null && !testing,
                loading = testing,
                onClick = {
                    testing = true
                    result = null
                    scope.launch {
                        result = ServerConnectionTester.test(config)
                        testing = false
                    }
                },
                height = 46.dp,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                label = "Enregistrer",
                enabled = error == null && !testing,
                onClick = { ServerConfigHolder.save(context, config) },
                height = 46.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    var hidden by remember { mutableStateOf(secret) }
    Column(modifier = modifier) {
        Text(label, color = ChocoColors.text, fontFamily = ChocoFonts.bodyMedium, fontSize = 14.sp)
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF111111))
                .border(1.dp, ChocoColors.borderStrong, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                // Typing again always masks a secret back, even if shown.
                onValueChange = { onValueChange(it); if (secret) hidden = true },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else keyboardType,
                    autoCorrectEnabled = false,
                ),
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp),
                cursorBrush = SolidColor(ChocoColors.accent),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            if (secret) {
                Icon(
                    AppIcon.of(if (hidden) "visibility_off" else "visibility"),
                    contentDescription = if (hidden) "Afficher" else "Masquer",
                    tint = ChocoColors.text,
                    modifier = Modifier.padding(start = 10.dp).size(20.dp).clickable { hidden = !hidden },
                )
            }
        }
    }
}

/** The form as a dialog — opened from [ServerSettingsButton]. */
@Composable
fun ServerConfigDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            ServerConfigForm(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Gear button (top-right of the launch/login screens) opening the form. */
@Composable
fun ServerSettingsButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconCircleButton(
        icon = AppIcon.of("settings"),
        size = 40.dp,
        iconSize = 22.dp,
        background = ChocoColors.card,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) ServerConfigDialog(onDismiss = { open = false })
}

/** First launch (nothing saved yet): the form is the whole app until saved. */
@Composable
fun ServerSetupScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChocoColors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.logo_mark),
            contentDescription = null,
            modifier = Modifier.padding(top = 34.dp, bottom = 28.dp).size(76.dp).clip(RoundedCornerShape(20.dp)),
        )
        ServerConfigForm(modifier = Modifier.fillMaxWidth())
    }
}
