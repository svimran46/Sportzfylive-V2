package com.sportzfy.live.core.player

/**
 * Ad, pop-under and redirect networks commonly injected into sports embeds.
 * Matched as a host or any of its subdomains. The main-frame lock in
 * [PlayerUrlPolicy] stops redirects from unlisted hosts; this list also stops
 * their scripts and subframes from loading at all.
 */
object AdHosts {
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
        // Push-notification spam
        "pushnami.com",
        "onesignal.com",
    )
}
