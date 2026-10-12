package com.sportzfy.live.core.player

import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.PlayerMode

/** Player behaviour derived from the remote [AppConfig]. */
data class PlayerConfig(
    val mode: PlayerMode = PlayerMode.IFRAME,
    val blockedHosts: Set<String> = AdHosts.DEFAULT,
    /**
     * Hosts the page itself may live on besides `embed.st`. Live TV adds the
     * channel's own host, so direct mode can open that channel's page.
     */
    val trustedHosts: Set<String> = emptySet(),
) {
    companion object {
        fun from(config: AppConfig): PlayerConfig = PlayerConfig(
            mode = config.playerMode,
            blockedHosts = (if (config.adBlocking) AdHosts.DEFAULT else emptySet()) + config.extraBlockedHosts,
        )
    }
}
