package com.sportzfy.live.core.time

import java.util.TimeZone

/**
 * The timezone that defines "today" and "tomorrow": the device's own local
 * timezone. Injectable so date boundaries are testable.
 */
fun interface ZoneProvider {
    fun zone(): TimeZone

    companion object {
        val System = ZoneProvider { TimeZone.getDefault() }
    }
}
