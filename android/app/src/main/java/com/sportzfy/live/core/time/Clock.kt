package com.sportzfy.live.core.time

/** Wall-clock source, injectable so caching and status rules are testable. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
