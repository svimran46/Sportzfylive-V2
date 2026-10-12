package com.sportzfy.live.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sportzfy.live.presentation.catalog.MatchCatalogScreen
import com.sportzfy.live.presentation.watch.WatchStudioScreen

@Composable
fun SportzfyNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = CatalogRoute, modifier = modifier) {
        composable<CatalogRoute> {
            MatchCatalogScreen(onOpenMatch = { id -> navController.navigate(WatchRoute(id)) })
        }
        composable<WatchRoute> {
            WatchStudioScreen(onBack = { navController.popBackStack() })
        }
    }
}
