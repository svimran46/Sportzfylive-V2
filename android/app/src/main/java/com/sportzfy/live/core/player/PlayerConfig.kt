package com.sportzfy.live.core.player

import com.sportzfy.live.core.network.StreamedEndpoints
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.PlayerMode

/** Player behaviour derived from the remote [AppConfig]. */
data class PlayerConfig(
    val mode: PlayerMode = PlayerMode.DIRECT,
    val blockedHosts: Set<String> = AdHosts.DEFAULT,
    /**
     * Hosts the page itself may live on besides `embed.st`. Live TV adds the
     * channel's own host, so direct mode can open that channel's page.
     */
    val trustedHosts: Set<String> = emptySet(),
    /** Browser-like navigation (see [PlayerUrlPolicy] openWeb): on for every stream. */
    val openWeb: Boolean = true,
    /**
     * Referer sent when the page is loaded directly; null sends none. Matches
     * send streamed.pk's, as its own embed iframe does; Live TV sends none.
     */
    val referer: String? = StreamedEndpoints.REFERER,
) {
    companion object {
        fun from(config: AppConfig): PlayerConfig = PlayerConfig(
            mode = config.playerMode,
            blockedHosts = (if (config.adBlocking) AdHosts.DEFAULT else emptySet()) + config.extraBlockedHosts,
        )
    }
}
