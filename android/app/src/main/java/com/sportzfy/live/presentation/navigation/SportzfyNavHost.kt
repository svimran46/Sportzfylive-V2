package com.sportzfy.live.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sportzfy.live.presentation.catalog.CatalogViewModel
import com.sportzfy.live.presentation.catalog.MatchCatalogScreen
import com.sportzfy.live.presentation.components.BottomNavBar
import com.sportzfy.live.presentation.livetv.LiveTvScreen
import com.sportzfy.live.presentation.model.DayScope
import com.sportzfy.live.presentation.watch.WatchStudioScreen
import org.koin.compose.viewmodel.koinViewModel

private const val TAB_FADE_MILLIS = 180

/**
 * App shell. The four tabs show the bottom bar; the player is full-screen.
 * Back from any tab returns to Today, and back from Today leaves the app.
 */
@Composable
fun SportzfyNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    // Created outside the NavHost, so it is Activity-scoped and shared by the
    // tabs: search and filters carry across Today, Tomorrow and All Matches.
    val catalogViewModel: CatalogViewModel = koinViewModel()

    // Each tab passes its own identity: NavHost builds its graph once, so the
    // destinations must not capture state that changes between compositions.
    val bottomBar: @Composable (MainTab) -> Unit = { tab -> MainBottomBar(tab, navController) }
    val openMatch: (String) -> Unit = { id -> navController.navigate(WatchRoute(id)) }

    NavHost(
        navController = navController,
        startDestination = TodayRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(TAB_FADE_MILLIS)) },
        exitTransition = { fadeOut(tween(TAB_FADE_MILLIS)) },
    ) {
        composable<TodayRoute> {
            MatchCatalogScreen(DayScope.TODAY, openMatch, viewModel = catalogViewModel, bottomBar = { bottomBar(MainTab.TODAY) })
        }
        composable<TomorrowRoute> {
            MatchCatalogScreen(DayScope.TOMORROW, openMatch, viewModel = catalogViewModel, bottomBar = { bottomBar(MainTab.TOMORROW) })
        }
        composable<LiveTvRoute> {
            LiveTvScreen(bottomBar = { bottomBar(MainTab.LIVE_TV) })
        }
        composable<AllMatchesRoute> {
            MatchCatalogScreen(DayScope.ALL, openMatch, viewModel = catalogViewModel, bottomBar = { bottomBar(MainTab.ALL_MATCHES) })
        }
        composable<WatchRoute> {
            WatchStudioScreen(onBack = { navController.popBackStack() })
        }
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
