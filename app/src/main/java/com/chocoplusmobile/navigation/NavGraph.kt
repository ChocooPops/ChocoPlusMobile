package com.chocoplusmobile.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chocoplusmobile.state.AppViewModel
import com.chocoplusmobile.state.LocalAppViewModel
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.state.StartPage
import com.chocoplusmobile.screens.auth.LoginScreen
import com.chocoplusmobile.screens.auth.SignupScreen
import com.chocoplusmobile.screens.auth.WelcomeBackScreen
import com.chocoplusmobile.screens.browse.BrowseScreen
import com.chocoplusmobile.screens.catalogue.CatalogueScreen
import com.chocoplusmobile.screens.detail.LicenseDetailScreen
import com.chocoplusmobile.screens.downloads.DownloadsScreen
import com.chocoplusmobile.screens.detail.MovieDetailScreen
import com.chocoplusmobile.screens.detail.SerieDetailScreen
import com.chocoplusmobile.screens.home.HomeScreen
import com.chocoplusmobile.screens.mylist.MyListScreen
import com.chocoplusmobile.screens.player.PlayerScreen
import com.chocoplusmobile.screens.profile.AdminUsersScreen
import com.chocoplusmobile.screens.profile.DocumentationScreen
import com.chocoplusmobile.screens.profile.EditProfileScreen
import com.chocoplusmobile.screens.profile.HistoryScreen
import com.chocoplusmobile.screens.profile.ProfileScreen
import com.chocoplusmobile.screens.profile.SettingsScreen
import com.chocoplusmobile.screens.profile.StatsScreen
import com.chocoplusmobile.screens.profile.SupportScreen
import com.chocoplusmobile.screens.search.SearchScreen
import com.chocoplusmobile.api.MediaType
import com.chocoplusmobile.ui.components.BottomNavBar
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.download.DownloadManager
import com.chocoplusmobile.download.DownloadStatus
import com.chocoplusmobile.ui.components.ButtonVariant
import com.chocoplusmobile.ui.components.PrimaryButton
import com.chocoplusmobile.ui.icons.AppIcon
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Root navigation — mirrors the RN app's RootNavigator.tsx (top-level: auth
 * vs. main, plus the welcome gate) + MainTabs.tsx/TabStack.tsx (bottom tabs,
 * each screen also reachable as a push from any tab). Compose Navigation's
 * flat single-NavHost model needs none of TabStack's "duplicate this screen
 * in every tab's own stack" workaround — every non-tab screen is just
 * registered once and pushed on top of whichever tab you're currently on,
 * which is what that workaround was emulating in the first place.
 */
@Composable
fun ChocoNavGraph(appViewModel: AppViewModel) {
    val authChecked by appViewModel.authChecked.collectAsState()
    val isAuthenticated by appViewModel.isAuthenticated.collectAsState()
    val welcomeGatePending by appViewModel.welcomeGatePending.collectAsState()
    val offlineSession by appViewModel.offlineSession.collectAsState()

    CompositionLocalProvider(LocalAppViewModel provides appViewModel) {
        when {
            offlineSession -> OfflineGraph()
            !authChecked -> SplashScreen()
            isAuthenticated && welcomeGatePending -> WelcomeBackScreen()
            isAuthenticated -> MainGraph()
            else -> AuthGraph()
        }
    }
}

/** The auth check can hang for the full network timeout on a bad
 * connection — after a few seconds, offer the offline mode right here
 * (only when there's something downloaded to watch). */
@Composable
private fun SplashScreen() {
    val appViewModel = LocalAppViewModel.current
    val hasDownloads = rememberHasOfflineContent()
    var showOffline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3000)
        showOffline = true
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ChocoColors.accent)
        if (showOffline && hasDownloads) {
            OfflineModeButton(
                onClick = appViewModel::enterOfflineSession,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(24.dp).fillMaxWidth(),
            )
        }
    }
}

/** True when at least one video is fully downloaded — the offline entry
 * points are hidden otherwise (there'd be nothing to show). */
@Composable
fun rememberHasOfflineContent(): Boolean {
    val entries by DownloadManager.entries.collectAsState()
    return entries.values.any { it.status == DownloadStatus.COMPLETED }
}

@Composable
fun OfflineModeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(
        label = "Mode hors ligne",
        icon = AppIcon.of("download_for_offline"),
        variant = ButtonVariant.OUTLINE,
        onClick = onClick,
        height = 48.dp,
        modifier = modifier,
    )
}

/**
 * Manually-entered offline session (AppViewModel.offlineSession): just the
 * Downloads screen and the player — no account, no tab bar, nothing that
 * would call the backend. The Downloads screen's own header offers the way
 * back to the normal start flow.
 */
@Composable
private fun OfflineGraph() {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalNavController provides navController) {
        Box(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
            NavHost(navController = navController, startDestination = "downloads", modifier = Modifier.fillMaxSize()) {
                composable("downloads") { DownloadsScreen() }
                composable("player") { PlayerScreen() }
            }
        }
    }
}

@Composable
private fun AuthGraph() {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(navController = navController, startDestination = "login") {
            composable("login") { LoginScreen() }
            composable("signup") { SignupScreen() }
        }
    }
}

private val BOTTOM_TAB_ROUTES = setOf("home", "films", "series", "catalogue", "mylist")

/** Mirrors the RN app's RootNavigator.tsx: MovieDetail/SerieDetail/Player
 * are registered at the root stack there specifically so they take over the
 * full screen and hide the tab bar — every other screen (Catalogue,
 * LicenseDetail, the Profile sub-pages) lives inside a tab's own stack so
 * the tab bar stays visible. Same split here, just expressed as "which
 * routes hide the bottom bar overlay" instead of "which navigator a screen
 * is registered in".
 *
 * Search and Profile are a deliberate departure from that RN mirror, per
 * user request: they should stand on their own, without the tab bar
 * overlaid on top of them, even though they're each still reached the same
 * way as before — the search icon and the avatar stay right where they are
 * in TopBar, only the destination screen's own presentation changes. */
private val FULL_SCREEN_ROUTES = setOf("movieDetail", "serieDetail", "player", "search", "profile")

/** Maps the Settings "page d'ouverture de l'app" choice to the route it
 * should land on — kept a plain function (not baked into NavHost's
 * `startDestination`, which must be a fixed literal known at graph-build
 * time) so MainGraph can navigate there once as soon as prefs are hydrated. */
private fun startPageRoute(page: StartPage): String = when (page) {
    StartPage.ACCUEIL -> "home"
    StartPage.FILMS -> "films"
    StartPage.SERIES -> "series"
    StartPage.CATALOGUE -> "catalogue"
    StartPage.MA_LISTE -> "mylist"
    StartPage.PROFIL -> "profile"
    StartPage.RECHERCHE -> "search"
}

/**
 * Deliberately not a Scaffold — mirrors the RN app's CustomTabBar.tsx, which
 * is `position: absolute` over the screen content with a gradient fading
 * from transparent to black, so whatever's scrolled underneath is dimly
 * visible through it. A Scaffold's `bottomBar` slot reserves its own space
 * and pushes content up above it instead, which reads as a solid opaque bar
 * once the app's own near-black background sits behind it — this Box
 * overlay is what actually keeps it looking translucent.
 */
@Composable
private fun MainGraph() {
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val prefsHydrated by appViewModel.prefsHydrated.collectAsState()
    val navController = rememberNavController()
    CompositionLocalProvider(LocalNavController provides navController) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route?.substringBefore("/")?.substringBefore("?")
        var activeTab by remember { mutableStateOf("home") }
        LaunchedEffect(currentRoute) {
            if (currentRoute in BOTTOM_TAB_ROUTES) activeTab = currentRoute!!
        }

        // The graph itself always has to build from a fixed "home" (NavHost's
        // startDestination can't itself depend on async state) — the user's
        // actual chosen start page is swapped in right after, the moment
        // prefs are confirmed hydrated, with "home" popped off the back
        // stack so it never lingers behind it (no flash, no extra back-tap).
        var startPageApplied by remember { mutableStateOf(false) }
        // Started offline (see AppViewModel.offlineMode): every other page
        // would just show network errors, so the downloads are the start
        // page regardless of the setting.
        val offlineMode by appViewModel.offlineMode.collectAsState()
        LaunchedEffect(prefsHydrated) {
            if (prefsHydrated && !startPageApplied) {
                startPageApplied = true
                val route = if (offlineMode) "downloads" else startPageRoute(prefs.settings.startPage)
                if (route != "home") {
                    navController.navigate(route) { popUpTo("home") { inclusive = true } }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(ChocoColors.bg)) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("home") { HomeScreen() }
                composable("films") { BrowseScreen(mediaType = MediaType.MOVIE) }
                composable("series") { BrowseScreen(mediaType = MediaType.SERIES) }
                composable("catalogue") { CatalogueScreen() }
                composable("mylist") { MyListScreen() }
                composable("profile") { ProfileScreen() }

                composable(
                    "search?query={query}",
                    arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
                ) { entry -> SearchScreen(initialQuery = entry.arguments?.getString("query").orEmpty()) }

                composable(
                    "licenseDetail/{id}?title={title}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.IntType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    LicenseDetailScreen(
                        licenseId = entry.arguments?.getInt("id") ?: 0,
                        fallbackTitle = entry.arguments?.getString("title").orEmpty(),
                    )
                }

                composable("history") { HistoryScreen() }
                composable("stats") { StatsScreen() }
                composable("settings") { SettingsScreen() }
                composable("support") { SupportScreen() }
                composable("editProfile") { EditProfileScreen() }
                composable("adminUsers") { AdminUsersScreen() }
                composable("documentation") { DocumentationScreen() }
                composable("downloads") { DownloadsScreen() }

                composable("movieDetail") { MovieDetailScreen() }
                composable("serieDetail") { SerieDetailScreen() }
                composable("player") { PlayerScreen() }
            }

            if (currentRoute !in FULL_SCREEN_ROUTES) {
                BottomNavBar(
                    currentRoute = activeTab,
                    onSelect = { route ->
                        activeTab = route
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
