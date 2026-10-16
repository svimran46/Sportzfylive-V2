package com.sportzfy.live.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sportzfy.live.BuildConfig
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.presentation.about.AboutScreen
import com.sportzfy.live.presentation.catalog.CatalogViewModel
import com.sportzfy.live.presentation.catalog.MatchCatalogScreen
import com.sportzfy.live.presentation.components.BottomNavBar
import com.sportzfy.live.presentation.livetv.LiveTvScreen
import com.sportzfy.live.presentation.livetv.player.TvNowPlaying
import com.sportzfy.live.presentation.livetv.player.TvPlayerLayer
import com.sportzfy.live.presentation.livetv.player.TvPlayerViewModel
import com.sportzfy.live.presentation.livetv.player.rememberTvDockAnchor
import com.sportzfy.live.presentation.model.AppSection
import com.sportzfy.live.presentation.model.DayScope
import com.sportzfy.live.presentation.model.DrawerItem
import com.sportzfy.live.presentation.model.DrawerSelection
import com.sportzfy.live.presentation.model.HomeView
import com.sportzfy.live.presentation.settings.ProvideUserSettings
import com.sportzfy.live.presentation.settings.SettingsScreen
import com.sportzfy.live.presentation.watch.WatchStudioScreen
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private const val TAB_FADE_MILLIS = 180

/**
 * App shell. The four tabs show the bottom bar and the drawer's menu button;
 * the match player, Settings and About are full-screen. The Live TV player
 * is drawn above every screen ([TvPlayerLayer]): docked on the Live TV tab,
 * a floating mini player everywhere else.
 * Back from any tab returns to Today, and back from Today leaves the app.
 */
@Composable
fun SportzfyNavHost(modifier: Modifier = Modifier) {
    ProvideUserSettings {
        val navController = rememberNavController()
        // Created outside the NavHost, so it is Activity-scoped and shared by the
        // tabs: search and filters carry across Today, Tomorrow and All Matches.
        val catalogViewModel: CatalogViewModel = koinViewModel()
        // Activity-scoped too: a channel keeps playing while the user changes tab.
        val tvPlayer: TvPlayerViewModel = koinViewModel()
        val tvDock = rememberTvDockAnchor()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

        ModalNavigationDrawer(
            drawerState = drawerState,
            // Opened from the menu button only, so horizontal swipes on pills,
            // rails and the player never pull it out by accident.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = { AppDrawer(navController, catalogViewModel, onClose = closeDrawer) },
            scrimColor = Color.Black.copy(alpha = 0.6f),
            modifier = modifier,
        ) {
            // Each tab passes its own identity: NavHost builds its graph once, so the
            // destinations must not capture state that changes between compositions.
            val bottomBar: @Composable (MainTab) -> Unit = { tab -> MainBottomBar(tab, navController) }
            // Taps on a screen that is still fading in or out are ignored, so
            // a double tap can't push a player twice or pop past the tab.
            val openMatch: (String) -> Unit = { id -> navController.ifSettled { navigate(WatchRoute(id)) } }
            val back: () -> Unit = { navController.ifSettled { popBackStack() } }

            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = TodayRoute,
                    enterTransition = { fadeIn(tween(TAB_FADE_MILLIS)) },
                    exitTransition = { fadeOut(tween(TAB_FADE_MILLIS)) },
                ) {
                    composable<TodayRoute> {
                        MatchCatalogScreen(
                            DayScope.TODAY,
                            openMatch,
                            viewModel = catalogViewModel,
                            bottomBar = { bottomBar(MainTab.TODAY) },
                            onBrowseTomorrow = { navController.navigateToTab(MainTab.TOMORROW) },
                            onOpenMenu = openDrawer,
                        )
                    }
                    composable<TomorrowRoute> {
                        MatchCatalogScreen(
                            DayScope.TOMORROW,
                            openMatch,
                            viewModel = catalogViewModel,
                            bottomBar = { bottomBar(MainTab.TOMORROW) },
                            onOpenMenu = openDrawer,
                        )
                    }
                    composable<LiveTvRoute> {
                        val tv by tvPlayer.state.collectAsStateWithLifecycle()
                        LiveTvScreen(
                            onPlayChannel = tvPlayer::play,
                            playingChannelId = tv.channel?.id,
                            nowPlaying = { TvNowPlaying(tvPlayer, tvDock) },
                            bottomBar = { bottomBar(MainTab.LIVE_TV) },
                            onOpenMenu = openDrawer,
                        )
                    }
                    composable<AllMatchesRoute> {
                        MatchCatalogScreen(
                            DayScope.ALL,
                            openMatch,
                            viewModel = catalogViewModel,
                            bottomBar = { bottomBar(MainTab.ALL_MATCHES) },
                            onOpenMenu = openDrawer,
                        )
                    }
                    composable<WatchRoute> {
                        WatchStudioScreen(onBack = back)
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(onBack = back, versionName = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE)
                    }
                    composable<AboutRoute> {
                        AboutScreen(onBack = back, versionName = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE)
                    }
                }
                // After the NavHost: drawn on top, and its full-screen Back wins.
                TvPlayerLayer(
                    viewModel = tvPlayer,
                    anchor = tvDock,
                    onOpenLiveTv = { navController.navigateToTab(MainTab.LIVE_TV) },
                )
            }
            StopTvForMatches(navController, tvPlayer)
            // Registered after the NavHost so it wins over its back handling:
            // Back closes an open drawer before it changes screen.
            BackHandler(enabled = drawerState.isOpen) { closeDrawer() }
        }
    }
}

/** A match stream and a TV channel never play together: opening a match stops the channel. */
@Composable
private fun StopTvForMatches(navController: NavController, tvPlayer: TvPlayerViewModel) {
    val entry by navController.currentBackStackEntryAsState()
    val watching = entry?.destination?.hasRoute<WatchRoute>() == true
    LaunchedEffect(watching) {
        if (watching) tvPlayer.close()
    }
}

/** Runs [action] only when the current screen has finished its transition. */
private inline fun NavController.ifSettled(action: NavController.() -> Unit) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) action()
}

/** Reads navigation and filter state here, not in the shell, so the NavHost graph never rebuilds. */
@Composable
private fun AppDrawer(navController: NavController, catalogViewModel: CatalogViewModel, onClose: () -> Unit) {
    val entry by navController.currentBackStackEntryAsState()
    val catalog by catalogViewModel.state(DayScope.ALL).collectAsStateWithLifecycle()
    SportzfyDrawer(
        selected = DrawerSelection.selected(entry?.destination.toSection(), catalog),
        versionName = BuildConfig.VERSION_NAME,
        onSelect = { item ->
            onClose()
            navController.openDrawerItem(item, catalogViewModel)
        },
        onClose = onClose,
    )
}

private fun NavDestination?.toSection(): AppSection = when {
    this == null -> AppSection.OTHER
    hasRoute<TodayRoute>() -> AppSection.HOME
    hasRoute<TomorrowRoute>() -> AppSection.TOMORROW
    hasRoute<LiveTvRoute>() -> AppSection.LIVE_TV
    hasRoute<AllMatchesRoute>() -> AppSection.ALL_MATCHES
    else -> AppSection.OTHER
}

/**
 * Every drawer entry lands on a real screen. Filters live in the shared
 * catalog ViewModel, so the bottom bar and the drawer never disagree.
 */
private fun NavController.openDrawerItem(item: DrawerItem, catalog: CatalogViewModel) {
    when (item) {
        DrawerItem.HOME -> {
            catalog.resetFilters()
            catalog.setHomeView(HomeView.LIVE)
            navigateToTab(MainTab.TODAY)
        }
        DrawerItem.LIVE_TV -> navigateToTab(MainTab.LIVE_TV)
        DrawerItem.FIXTURES -> {
            catalog.resetFilters()
            navigateToTab(MainTab.ALL_MATCHES)
        }
        DrawerItem.SAVED -> {
            catalog.showFavorites()
            navigateToTab(MainTab.ALL_MATCHES)
        }
        DrawerItem.SEARCH -> {
            catalog.openSearch()
            navigateToTab(MainTab.ALL_MATCHES)
        }
        DrawerItem.FOOTBALL -> {
            catalog.selectCategory(SportCategory.FOOTBALL)
            navigateToTab(MainTab.ALL_MATCHES)
        }
        DrawerItem.CRICKET -> {
            catalog.selectCategory(SportCategory.CRICKET)
            navigateToTab(MainTab.ALL_MATCHES)
        }
        DrawerItem.OTHER_SPORTS -> {
            catalog.showOtherSports()
            navigateToTab(MainTab.ALL_MATCHES)
        }
        DrawerItem.SETTINGS -> navigate(SettingsRoute) { launchSingleTop = true }
        DrawerItem.ABOUT -> navigate(AboutRoute) { launchSingleTop = true }
    }
}

/** Bottom bar for [selected]; hidden while the keyboard is open so it never sits on the IME. */
@Composable
private fun MainBottomBar(selected: MainTab, navController: NavController) {
    // Measured, not WindowInsets.isImeVisible: a window without real insets
    // reports every type as "visible", which would hide the bar for good.
    if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) return
    BottomNavBar(
        items = MainTab.items,
        selectedKey = selected.item.key,
        onSelect = { key -> navController.navigateToTab(MainTab.fromKey(key)) },
    )
}

/** Standard tab switch: one copy of each tab, state saved and restored, Today at the root. */
private fun NavController.navigateToTab(tab: MainTab) {
    val options: NavOptionsBuilder.() -> Unit = {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    when (tab) {
        MainTab.TODAY -> navigate(TodayRoute, options)
        MainTab.TOMORROW -> navigate(TomorrowRoute, options)
        MainTab.LIVE_TV -> navigate(LiveTvRoute, options)
        MainTab.ALL_MATCHES -> navigate(AllMatchesRoute, options)
    }
}
