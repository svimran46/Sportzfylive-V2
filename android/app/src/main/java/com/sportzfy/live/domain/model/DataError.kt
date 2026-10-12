package com.sportzfy.live.domain.model

/** Why upstream data could not be loaded, in terms the UI can explain. */
enum class DataError {
    /** No connectivity, DNS failure or timeout. */
    NETWORK,

    /** Upstream answered 429. */
    RATE_LIMITED,

    /** Upstream's DDoS protection rejected the request (403). */
    BLOCKED,

    /** Upstream answered 5xx. */
    UNAVAILABLE,

    UNKNOWN,
}
