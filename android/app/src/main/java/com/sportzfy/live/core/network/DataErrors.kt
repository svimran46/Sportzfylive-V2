package com.sportzfy.live.core.network

import com.sportzfy.live.domain.model.DataError
import retrofit2.HttpException
import java.io.IOException

fun Throwable?.toDataError(): DataError = when (this) {
    is HttpException -> httpStatusToDataError(code())
    is IOException -> DataError.NETWORK
    else -> DataError.UNKNOWN
}

fun httpStatusToDataError(code: Int): DataError = when {
    code == 429 -> DataError.RATE_LIMITED
    code == 403 -> DataError.BLOCKED
    code >= 500 -> DataError.UNAVAILABLE
    else -> DataError.UNKNOWN
}
