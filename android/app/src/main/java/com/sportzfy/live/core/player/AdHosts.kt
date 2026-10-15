package com.sportzfy.live.core.player

/**
 * Ad, pop-under and redirect networks commonly injected into sports embeds.
 * Matched as a host or any of its subdomains (see [PlayerUrlPolicy]).
 */
object AdHosts {
    /**
     * Pop-under and redirect networks the top-level page may never navigate
     * to, whatever the remote config says. Their scripts still load: the
     * embeds' inline player setup calls into them.
     */
    val ALWAYS_BLOCKED: Set<String> = setOf(
        "popads.net",
        "popcash.net",
        "adsterra.com",
        "propellerads.com",
        "doubleclick.net",
        "googlesyndication.com",
        "onclkds.com",
        "acscdn.com",
        "histats.com",
    )

    /** Top-level navigations to these are refused when ad blocking is on. */
    val DEFAULT: Set<String> = setOf(
        // Display / programmatic
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "revcontent.com",
        // Pop-under / redirect networks
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "onclickads.net",
        "adsterra.com",
        "highperformanceformat.com",
        "exoclick.com",
        "exosrv.com",
        "juicyads.com",
        "adcash.com",
        "hilltopads.net",
        "clickadu.com",
        "a-ads.com",
        "trafficjunky.net",
        "trafficstars.com",
        "tsyndicate.com",
        "galaksion.com",
        "admaven.com",
        "ad-maven.com",
        "monetag.com",
        "bidgear.com",
        "onclkds.com",
        "acscdn.com",
        "histats.com",
        // Push-notification spam
        "pushnami.com",
        "onesignal.com",
    )
}
