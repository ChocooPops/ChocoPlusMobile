package com.chocoplusmobile.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.R
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ButtonVariant
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t

/** Mirrors the RN app's screens/Auth/LoginScreen.tsx. */
@Composable
fun LoginScreen() {
    val appViewModel = LocalAppViewModel.current
    val navController = LocalNavController.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val serverUnavailable by appViewModel.serverUnavailable.collectAsState()
    val checkingServer by appViewModel.checkingServer.collectAsState()
    val hasDownloads = com.chocoplusmobile.navigation.rememberHasOfflineContent()

    // Is the backend even up? Without a stored token the boot never asked.
    LaunchedEffect(Unit) { appViewModel.checkServer() }

    val canSubmit = email.trim().isNotEmpty() && password.isNotEmpty() && !loading

    fun submit() {
        if (!canSubmit) return
        error = null
        loading = true
        appViewModel.login(
            email.trim(),
            password,
            // An unreachable server gets the banner below, not the
            // credentials error box (that one stays for 401/403 & co).
            onError = { message -> error = if (appViewModel.serverUnavailable.value) null else message; loading = false },
            onSuccess = { loading = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChocoColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 30.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)))
        }

        Text(
            t(Tr.LOGIN_TITLE),
            color = ChocoColors.text,
            fontFamily = ChocoFonts.headingExtraBold,
            fontSize = 25.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 34.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Column(modifier = Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            LoginField(
                icon = "mail",
                value = email,
                onValueChange = { email = it },
                placeholder = t(Tr.LOGIN_EMAIL),
                keyboardType = KeyboardType.Email,
                enabled = !loading,
            )
            LoginField(
                icon = "lock",
                value = password,
                // Typing again always masks it back, even if it was shown.
                onValueChange = { password = it; hidden = true },
                placeholder = t(Tr.LOGIN_PASSWORD),
                // Password (even while shown in clear): no keyboard
                // suggestions or learning of what's typed here.
                keyboardType = KeyboardType.Password,
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                enabled = !loading,
                trailing = {
                    androidx.compose.material3.Icon(
                        AppIcon.of(if (hidden) "visibility_off" else "visibility"),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0x4DFFFFFF),
                        modifier = Modifier.size(20.dp).clickable { hidden = !hidden },
                    )
                },
            )
        }

        if (serverUnavailable) {
            ServerUnavailableBanner(
                hasDownloads = hasDownloads,
                checking = checkingServer,
                onOpenDownloads = appViewModel::enterOfflineSession,
                onRetry = appViewModel::checkServer,
            )
        }

        if (error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(ChocoColors.dangerSoft, RoundedCornerShape(11.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Icon(AppIcon.of("error_outline"), contentDescription = null, tint = ChocoColors.danger, modifier = Modifier.size(17.dp))
                Text(error!!, color = androidx.compose.ui.graphics.Color(0xFFFF9C9C), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.5.sp, lineHeight = 18.sp)
            }
        }

        PrimaryButton(label = t(Tr.LOGIN_SUBMIT), onClick = ::submit, loading = loading, enabled = canSubmit, height = 52.dp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(
            6.dp,
            Alignment.CenterHorizontally)
        ) {
            Text(t(Tr.LOGIN_NO_ACCOUNT), color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp)
            Text(
                t(Tr.CREATE_ACCOUNT),
                color = ChocoColors.accent,
                fontFamily = ChocoFonts.bodyBold,
                fontSize = 12.5.sp,
                modifier = Modifier.clickable(enabled = !loading) { navController.navigate("signup") }
            )
        }

    }
}

/** Shown while the backend is unreachable (no network, timeout, 5xx) —
 * logging in can't work then, but downloaded videos still can. */
@Composable
private fun ServerUnavailableBanner(hasDownloads: Boolean, checking: Boolean, onOpenDownloads: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(ChocoColors.dangerSoft, RoundedCornerShape(11.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(AppIcon.of("wifi_off"), contentDescription = null, tint = ChocoColors.danger, modifier = Modifier.size(17.dp))
            Text(
                t(Tr.LOGIN_SERVER_UNREACHABLE) +
                    // A wrong URL looks exactly like a server that's down.
                    t(Tr.LOGIN_SERVER_CONFIG_HINT) +
                    t(if (hasDownloads) Tr.LOGIN_DOWNLOADS_STILL_AVAILABLE else Tr.LOGIN_RETRY_LATER),
                color = androidx.compose.ui.graphics.Color(0xFFFF9C9C),
                fontFamily = ChocoFonts.bodyMedium,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        if (hasDownloads) {
            PrimaryButton(
                label = t(Tr.LOGIN_OPEN_DOWNLOADS),
                icon = AppIcon.of("download_for_offline"),
                onClick = onOpenDownloads,
                height = 44.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryButton(
            label = t(Tr.RETRY),
            icon = AppIcon.of("refresh"),
            variant = ButtonVariant.OUTLINE,
            loading = checking,
            onClick = onRetry,
            height = 40.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun LoginField(
    icon: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(ChocoColors.surfaceInput, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.Icon(AppIcon.of(icon), contentDescription = null, tint = androidx.compose.ui.graphics.Color(0x59FFFFFF), modifier = Modifier.size(20.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = { Text(placeholder, color = androidx.compose.ui.graphics.Color(0x59FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = keyboardType != KeyboardType.Password,
            ),
            visualTransformation = visualTransformation,
            textStyle = androidx.compose.ui.text.TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
