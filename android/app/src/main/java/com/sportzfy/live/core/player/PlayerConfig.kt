package com.sportzfy.live.core.player

import com.sportzfy.live.core.network.StreamedEndpoints
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.PlayerMode

/** Player behaviour derived from the remote [AppConfig]. */
data class PlayerConfig(
    val mode: PlayerMode = PlayerMode.DIRECT,
    /** Hosts whose pages may not load in any frame. */
    val blockedHosts: Set<String> = AdHosts.ALWAYS_BLOCKED + AdHosts.DEFAULT,
    /**
     * Hosts whose scripts and other sub-resources are refused: the pop-up
     * networks every stream blocks, plus the owner's extra hosts. The wider
     * ad list stays on navigations only, so embeds that check their ad
     * scripts still see them load.
     */
    val blockedResourceHosts: Set<String> = AdHosts.ALWAYS_BLOCKED,
    /**
     * Referer sent when the page is loaded directly; null sends none. Matches
     * send streamed.pk's, as its own embed iframe does; Live TV sends none.
     */
    val referer: String? = StreamedEndpoints.REFERER,
    /** Plain-http streams and pages may play. Cleartext is allowed app-wide; this is the switch. */
    val allowHttpStreams: Boolean = true,
    /** DASH and progressive (MPEG-TS, MP4…) streams play in Media3. HLS and web pages always do. */
    val extendedFormats: Boolean = true,
) {
    companion object {
        fun from(config: AppConfig): PlayerConfig = PlayerConfig(
            mode = config.playerMode,
            blockedHosts = AdHosts.ALWAYS_BLOCKED +
                (if (config.adBlocking) AdHosts.DEFAULT else emptySet()) +
                config.extraBlockedHosts,
            blockedResourceHosts = AdHosts.ALWAYS_BLOCKED + config.extraBlockedHosts,
        )
    }
}
