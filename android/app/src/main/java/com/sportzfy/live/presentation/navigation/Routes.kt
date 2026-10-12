package com.sportzfy.live.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object CatalogRoute

@Serializable
data class WatchRoute(val matchId: String)
