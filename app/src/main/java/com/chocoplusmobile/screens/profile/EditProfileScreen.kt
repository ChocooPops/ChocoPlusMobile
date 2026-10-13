package com.chocoplusmobile.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.api.ProfilPhoto
import com.chocoplusmobile.api.UpdateUserByUserPayload
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.Avatar
import com.chocoplusmobile.ui.components.ChocoBottomSheet
import com.chocoplusmobile.ui.components.DatePickerField
import com.chocoplusmobile.ui.components.FadeInImage
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.components.ScreenHeaderBack
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.launch

/** Mirrors the RN app's screens/Profile/EditProfileScreen.tsx. The photo
 * picker uses a LazyVerticalGrid inside the bottom sheet's non-scrollable
 * mode — same windowing reasoning as the RN side's FlatList fix (nothing
 * mounts/decodes until it's actually near the visible window). */
@Composable
fun EditProfileScreen() {
    val navController = LocalNavController.current
    val appViewModel = LocalAppViewModel.current
    val user by appViewModel.user.collectAsState()
    val scope = rememberCoroutineScope()

    var pseudo by remember(user?.id) { mutableStateOf(user?.pseudo ?: "") }
    var dateBorn by remember(user?.id) { mutableStateOf(user?.dateBorn ?: "") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // Client-side validation error (blocks the request entirely) — distinct
    // from resultMessage below, which is whatever the server itself says
    // happened (it can legitimately fail with a 200 + state:false, e.g.
    // wrong current password or pseudo too short).
    var error by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var resultSuccess by remember { mutableStateOf(false) }

    var photoPickerOpen by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(listOf<ProfilPhoto>()) }

    LaunchedEffect(Unit) {
        try {
            photos = NetworkModule.safeCall { NetworkModule.api.getProfilPhotos() }
        } catch (e: Exception) { /* ignore */ }
    }

    fun save() {
        val trimmedPseudo = pseudo.trim()
        val trimmedDate = dateBorn.trim()
        error = null
        resultMessage = null

        if (trimmedPseudo.length < 5) {
            error = "Le pseudo doit faire au moins 5 caractères."
            return
        }
        if (trimmedDate.isEmpty()) {
            error = "Merci d'indiquer une date de naissance."
            return
        }
        // The backend only applies a password change when currentPassWord,
        // newPassWord and reNewPassWord are ALL present — filling only some
        // of the three would silently do nothing server-side instead of
        // erroring, so this catches it client-side instead.
        val touchingPassword = currentPassword.isNotEmpty() || newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
        if (touchingPassword && (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty())) {
            error = "Remplis les 3 champs de mot de passe pour le changer."
            return
        }
        if (newPassword.isNotEmpty() && newPassword != confirmPassword) {
            error = "Les nouveaux mots de passe ne correspondent pas."
            return
        }

        saving = true
        scope.launch {
            try {
                val result = NetworkModule.safeCall {
                    NetworkModule.api.updateUserByUser(
                        UpdateUserByUserPayload(
                            pseudo = trimmedPseudo,
                            date = trimmedDate,
                            currentPassWord = currentPassword.ifEmpty { null },
                            newPassWord = newPassword.ifEmpty { null },
                            reNewPassWord = confirmPassword.ifEmpty { null },
                        ),
                    )
                }
                resultMessage = result.message
                resultSuccess = result.state
                if (result.state) {
                    appViewModel.refreshUser()
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }
            } catch (e: Exception) {
                error = e.message ?: "Mise à jour impossible"
            } finally {
                saving = false
            }
        }
    }

    fun pickPhoto(photo: ProfilPhoto) {
        photoPickerOpen = false
        scope.launch {
            try {
                NetworkModule.safeCall { NetworkModule.api.updateProfilePicture(photo.id) }
                appViewModel.refreshUser()
            } catch (e: Exception) { /* ignore — avatar keeps its previous value */ }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(top = 50.dp)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE)) {
            ScreenHeaderBack(title = "Gérer le profil", onBack = { navController.popBackStack() })

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(initial = user?.firstName?.firstOrNull()?.uppercase() ?: "?", uri = user?.profilPhoto, size = 88.dp, radius = 8.dp, editable = true, onClick = { photoPickerOpen = true })
                Text("Modifier la photo de profil", color = ChocoColors.accent, fontFamily = ChocoFonts.bodyMedium, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp).clickable { photoPickerOpen = true })
            }

            FieldLabel("Pseudo")
            EditTextField(pseudo, { pseudo = it })

            FieldLabel("Date de naissance", topPadding = 16.dp)
            DatePickerField(isoValue = dateBorn, onIsoValueChange = { dateBorn = it })

            Text("MOT DE PASSE", color = ChocoColors.textFaint, fontFamily = ChocoFonts.bodyBold, fontSize = 10.5.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 26.dp, bottom = 11.dp))
            FieldLabel("Mot de passe actuel")
            EditTextField(currentPassword, { currentPassword = it }, placeholder = "Requis pour changer de mot de passe", isPassword = true)
            FieldLabel("Nouveau mot de passe", topPadding = 16.dp)
            EditTextField(newPassword, { newPassword = it }, placeholder = "Laisser vide pour ne pas changer", isPassword = true)
            FieldLabel("Confirmer le nouveau mot de passe", topPadding = 16.dp)
            EditTextField(confirmPassword, { confirmPassword = it }, placeholder = "••••••••", isPassword = true)

            if (error != null) {
                Text(error!!, color = Color(0xFFFF9C9C), fontFamily = ChocoFonts.bodyMedium, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            }
            // The server's own message — can be several lines (one per
            // field it actually changed) or a single validation failure;
            // shown as-is either way, in French already (server responses
            // are localized via the Accept-Language header this app sends).
            if (resultMessage != null) {
                Text(
                    resultMessage!!.trim(),
                    color = if (resultSuccess) ChocoColors.accent else Color(0xFFFF9C9C),
                    fontFamily = ChocoFonts.bodyMedium,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            PrimaryButton(label = "Enregistrer les modifications", onClick = ::save, loading = saving, height = 50.dp, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        }
    }

    ChocoBottomSheet(visible = photoPickerOpen, onClose = { photoPickerOpen = false }, maxHeightPct = 0.6f, scrollable = false) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Choisir une photo", color = ChocoColors.text, fontFamily = ChocoFonts.headingExtraBold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(photos.size, key = { i -> "${photos[i].id}-$i" }) { i ->
                    val photo = photos[i]
                    // Grid is already lazy (LazyVerticalGrid) — only photos
                    // scrolled into view ever request their image. FadeInImage
                    // adds the other half: a grey circle while it loads, then
                    // a fade-in once decoded, instead of popping in abruptly.
                    FadeInImage(
                        uri = photo.name,
                        title = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(ChocoColors.surfaceAlt2).clickable { pickPhoto(photo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(text, color = ChocoColors.textTertiary, fontFamily = ChocoFonts.bodyBold, fontSize = 11.5.sp, modifier = Modifier.padding(top = topPadding, bottom = 7.dp))
}

@Composable
private fun EditTextField(value: String, onValueChange: (String) -> Unit, placeholder: String = "", isPassword: Boolean = false) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = Color(0x59FFFFFF), fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp) },
        singleLine = true,
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = TextStyle(color = ChocoColors.text, fontFamily = ChocoFonts.bodyRegular, fontSize = 14.5.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().height(48.dp).background(ChocoColors.surfaceInput, RoundedCornerShape(11.dp)),
    )
}
