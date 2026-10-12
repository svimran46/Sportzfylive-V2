package com.sportzfy.live.core.player

import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.PlayerMode

/** Player behaviour derived from the remote [AppConfig]. */
data class PlayerConfig(
    val mode: PlayerMode = PlayerMode.IFRAME,
    val blockedHosts: Set<String> = AdHosts.DEFAULT,
) {
    companion object {
        fun from(config: AppConfig): PlayerConfig = PlayerConfig(
            mode = config.playerMode,
            blockedHosts = (if (config.adBlocking) AdHosts.DEFAULT else emptySet()) + config.extraBlockedHosts,
        )
    }
}
