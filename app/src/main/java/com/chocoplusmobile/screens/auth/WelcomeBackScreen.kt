package com.chocoplusmobile.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chocoplusmobile.R
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.ui.components.Avatar
import com.chocoplusmobile.ui.components.ButtonVariant
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoFonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t

/**
 * Shown once per cold app launch when a stored token got silently restored
 * (see AppViewModel's welcomeGatePending) — an interactive login() skips
 * straight to the app instead. Tapping the photo plays a quick zoom/rebound,
 * then a brief loading spinner under the photo, before handing off to the
 * normal navigator. Mirrors the RN app's screens/Auth/WelcomeBackScreen.tsx.
 */
@Composable
fun WelcomeBackScreen() {
    val appViewModel = LocalAppViewModel.current
    val user by appViewModel.user.collectAsState()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    var entering by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    fun enter() {
        if (entering) return
        entering = true
        scope.launch {
            // Zoom in, then spring back down past 1 into a couple of
            // shrinking rebounds before settling — reads as a bounce.
            scale.animateTo(1.1f, animationSpec = tween(100))
            scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.35f, stiffness = 260f))
            loading = true
            // Held for a beat so the spinner is actually visible instead of
            // flashing for a single frame.
            delay(500)
            appViewModel.dismissWelcomeGate()
        }
    }

    val greeting = t(Tr.WELCOME_GREETING, (user?.pseudo ?: user?.firstName ?: "").replaceFirstChar { it.uppercase() })

    if (isLandscape) {
        // The portrait layout below stacks logo → text → a big 150dp avatar
        // → button vertically, with a fixed 130dp gap just to push the
        // button toward the bottom — all of that comfortably fits a tall
        // portrait screen, but stacked the same way on a short landscape
        // one it would overflow well past the fold. Split left/right
        // instead: nothing here needs to stack, and a Row uses the extra
        // width landscape actually has plenty of. verticalScroll is a
        // safety net for unusually short screens, not the primary layout.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ChocoColors.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo_wordmark), contentDescription = null, modifier = Modifier.size(90.dp).clip(RoundedCornerShape(16.dp)))
                Text(
                    greeting,
                    color = ChocoColors.text,
                    fontFamily = ChocoFonts.knewave,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(
                    initial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
                    uri = user?.profilPhoto,
                    size = 120.dp,
                    radius = 10.dp,
                    onClick = ::enter,
                    modifier = Modifier.scale(scale.value),
                )
                androidx.compose.foundation.layout.Box(modifier = Modifier.height(24.dp).padding(top = 14.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(color = ChocoColors.accent)
                }
                PrimaryButton(
                    label = t(Tr.LOG_OUT),
                    variant = ButtonVariant.OUTLINE,
                    onClick = { appViewModel.logout() },
                    enabled = !loading,
                    height = 46.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(horizontal = 24.dp).padding(top = 40.dp, bottom = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo_wordmark), contentDescription = null, modifier = Modifier.size(130.dp).clip(RoundedCornerShape(20.dp)))
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    greeting,
                    color = ChocoColors.text,
                    fontFamily = ChocoFonts.knewave,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.padding(bottom = 22.dp, top = 10.dp),
                )

                Avatar(
                    initial = user?.firstName?.firstOrNull()?.uppercase() ?: "?",
                    uri = user?.profilPhoto,
                    size = 150.dp,
                    radius = 10.dp,
                    onClick = ::enter,
                    modifier = Modifier.scale(scale.value),
                )

                androidx.compose.foundation.layout.Box(modifier = Modifier.height(24.dp).padding(top = 22.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(color = ChocoColors.accent)
                }

                PrimaryButton(
                    label = t(Tr.LOG_OUT),
                    variant = ButtonVariant.OUTLINE,
                    onClick = { appViewModel.logout() },
                    enabled = !loading,
                    height = 46.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 130.dp),
                )
            }
        }
    }
}
