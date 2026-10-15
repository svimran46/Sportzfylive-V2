package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.AppConfig
import kotlinx.coroutines.flow.StateFlow

interface AppConfigRepository {
    /** Last good config: cached on the device, then refreshed from GitHub. */
    val config: StateFlow<AppConfig>

    /** Re-fetches unless the last successful fetch is younger than the TTL. */
    suspend fun refresh(force: Boolean = false)
}
