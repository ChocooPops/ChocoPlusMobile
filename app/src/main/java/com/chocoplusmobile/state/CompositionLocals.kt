package com.chocoplusmobile.state

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** RN's useAppState()/useNavigation() are context hooks reachable from any
 * component — these are the direct Compose equivalent, provided once at the
 * NavGraph root, so screens/components don't need AppViewModel/NavController
 * threaded through every single signature. */
val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> {
    error("LocalAppViewModel not provided — wrap content in ChocoNavGraph")
}

val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("LocalNavController not provided — wrap content in ChocoNavGraph")
}
