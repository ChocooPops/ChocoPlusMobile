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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.R
import com.chocoplusmobile.api.ApiException
import com.chocoplusmobile.api.EmailPayload
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.RegisterPayload
import com.chocoplusmobile.api.SendVerificationCodePayload
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ActionStatus
import com.chocoplusmobile.ui.components.ActionStatusOverlay
import com.chocoplusmobile.ui.components.DatePickerField
import com.chocoplusmobile.ui.components.OtpCodeField
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.icons.AppIcon
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch

private enum class Step { IDENTITY, CODE, DONE }
private val STEP_ORDER = listOf(Step.IDENTITY, Step.CODE, Step.DONE)
private val STEP_LABELS = mapOf(Step.IDENTITY to "Identité", Step.CODE to "Code mail", Step.DONE to "Validation")

/** Mirrors the RN app's screens/Auth/SignupScreen.tsx — reworked against the
 * real auth routes (auth.controller.ts/auth.service.ts): send-verification-
 * code takes the full identity (RegisterUser: pseudo/firstName/lastName/
 * dateBorn/email, no password field at all), and register takes only
 * {verificationCode, email} — the rest was already cached server-side
 * against that email by send-verification-code. There genuinely is no
 * password step in signup: registerNewUser() hardcodes every new account's
 * password column to the literal string 'null', so a field for it here
 * would just mislead people into thinking it does something. */
@Composable
fun SignupScreen() {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.IDENTITY) }
    // The furthest step ever successfully reached — separate from `step`
    // (which one is on screen right now) so tapping back to an already-
    // validated step and then forward again is just a display change, not
    // a re-validation.
    var furthestStep by remember { mutableStateOf(Step.IDENTITY) }
    var actionStatus by remember { mutableStateOf<ActionStatus?>(null) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var pseudo by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var dateBorn by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val identityReady = firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty() && pseudo.trim().length >= 5 &&
        email.trim().isNotEmpty() && dateBorn.trim().isNotEmpty()

    fun advanceTo(target: Step) {
        step = target
        if (STEP_ORDER.indexOf(target) > STEP_ORDER.indexOf(furthestStep)) furthestStep = target
    }

    fun submitIdentity() {
        if (!identityReady) return
        actionStatus = ActionStatus.Loading
        scope.launch {
            actionStatus = try {
                val result = NetworkModule.safeCall {
                    NetworkModule.api.sendVerificationCode(
                        SendVerificationCodePayload(
                            pseudo = pseudo.trim(),
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            dateBorn = dateBorn.trim(),
                            email = email.trim(),
                        ),
                    )
                }
                if (result.state) advanceTo(Step.CODE)
                ActionStatus.Done(result.message.ifBlank { "Code envoyé." }, result.state)
            } catch (e: Exception) {
                ActionStatus.Done((e as? ApiException)?.message ?: "Impossible d'envoyer le code", success = false)
            }
        }
    }

    fun resendCode() {
        actionStatus = ActionStatus.Loading
        scope.launch {
            actionStatus = try {
                val result = NetworkModule.safeCall { NetworkModule.api.resendVerificationCode(EmailPayload(email.trim())) }
                ActionStatus.Done(result.message.ifBlank { "Code renvoyé." }, result.state)
            } catch (e: Exception) {
                ActionStatus.Done((e as? ApiException)?.message ?: "Impossible de renvoyer le code", success = false)
            }
        }
    }

    fun submitCode() {
        val codeInt = code.trim().takeIf { it.length == 6 }?.toIntOrNull() ?: return
        actionStatus = ActionStatus.Loading
        scope.launch {
            actionStatus = try {
                val result = NetworkModule.safeCall { NetworkModule.api.register(RegisterPayload(verificationCode = codeInt, email = email.trim())) }
                if (result.state) advanceTo(Step.DONE)
                ActionStatus.Done(result.message.ifBlank { "Compte créé." }, result.state)
            } catch (e: Exception) {
                ActionStatus.Done((e as? ApiException)?.message ?: "Code invalide ou expiré", success = false)
            }
        }
    }

    val stepIndex = STEP_ORDER.indexOf(step)
    val furthestIndex = STEP_ORDER.indexOf(furthestStep)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChocoColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 30.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)))
        }

        Text("Créer un compte", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 25.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 28.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STEP_ORDER.forEachIndexed { i, s ->
                val active = i <= stepIndex
                // Only Identité/Code mail are ever worth tapping back to —
                // and only once they've actually been reached, so this never
                // offers to skip ahead to a step that hasn't been validated
                // yet. Once the account is created there's nothing left to
                // revisit, so the whole stepper goes inert.
                val navigable = s != Step.DONE && i <= furthestIndex && furthestStep != Step.DONE
                Column(
                    modifier = Modifier.weight(1f).then(if (navigable) Modifier.clickable { step = s } else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(if (active) ChocoColors.accent else Color(0x24FFFFFF), RoundedCornerShape(99.dp)))
                    Text(STEP_LABELS[s]!!, color = if (active) ChocoColors.accent else ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        when (step) {
            Step.IDENTITY -> {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        PlainInput(firstName, { firstName = it }, "Prénom", modifier = Modifier.weight(1f))
                        PlainInput(lastName, { lastName = it }, "Nom", modifier = Modifier.weight(1f))
                    }
                    PlainInput(pseudo, { pseudo = it }, "Pseudo (5 caractères min.)")
                    PlainInput(email, { email = it }, "Adresse e-mail", keyboardType = KeyboardType.Email)
                    DatePickerField(isoValue = dateBorn, onIsoValueChange = { dateBorn = it }, height = 50.dp)
}

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).background(Color(0x12FFD301), RoundedCornerShape(12.dp)).padding(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(AppIcon.of("mark_email_unread"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(19.dp))
                    Text(
                        "Un code de vérification t'attend par mail. Ton compte sera ensuite validé par un administrateur avant le premier accès.",
                        color = ChocoColors.textSecondary,
                        fontFamily = ChocoFonts.bodyRegular,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    )
                }

                PrimaryButton(label = "Recevoir mon code", onClick = ::submitIdentity, loading = actionStatus is ActionStatus.Loading, enabled = identityReady, height = 52.dp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
            }

            Step.CODE -> {
                Text(
                    "Entre le code à 6 chiffres envoyé à\n$email",
                    color = ChocoColors.textSecondary,
                    fontFamily = ChocoFonts.bodyRegular,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                )
                OtpCodeField(value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth())
                PrimaryButton(label = "Valider mon compte", onClick = ::submitCode, loading = actionStatus is ActionStatus.Loading, enabled = code.trim().length == 6, height = 52.dp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
                Text(
                    "Renvoyer le code",
                    color = ChocoColors.accent,
                    fontFamily = ChocoFonts.bodyBold,
                    fontSize = 12.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickable(enabled = actionStatus !is ActionStatus.Loading, onClick = ::resendCode),
                    textAlign = TextAlign.Center,
                )
            }

            Step.DONE -> {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcon.of("task_alt"), contentDescription = null, tint = ChocoColors.accent, modifier = Modifier.size(30.dp))
                    Text("Compte créé !", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 19.sp)
                    Text(
                        "Un administrateur doit encore activer ton compte avant ta première connexion. Tu recevras un mail dès que c'est fait.",
                        color = ChocoColors.textSecondary,
                        fontFamily = ChocoFonts.bodyRegular,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    PrimaryButton(label = "Retour à la connexion", onClick = { navController.navigate("login") { popUpTo("login") { inclusive = true } } }, height = 48.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (step != Step.DONE) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(
                6.dp,
                Alignment.CenterHorizontally)
            ) {
                Text("Déjà inscrit ? ", color = ChocoColors.textMuted, fontFamily = ChocoFonts.bodyRegular, fontSize = 12.5.sp)
                Text(
                    "Se connecter",
                    color = ChocoColors.accent,
                    fontFamily = ChocoFonts.bodyBold,
                    fontSize = 12.5.sp,
                    modifier = Modifier.clickable(enabled = actionStatus !is ActionStatus.Loading) { navController.popBackStack() },
                )
            }
        }
    }

    ActionStatusOverlay(status = actionStatus, onDismiss = { actionStatus = null })
}

@Composable
private fun PlainInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0x59FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp),
        colors = clearFieldColors(),
        modifier = modifier.fillMaxWidth().height(50.dp).background(ChocoColors.surfaceInput, RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun clearFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
