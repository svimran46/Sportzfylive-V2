package com.sportzfy.live.core.network

import kotlinx.serialization.json.Json

/** Tolerant JSON config: upstream adds, drops and retypes fields without notice. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}
