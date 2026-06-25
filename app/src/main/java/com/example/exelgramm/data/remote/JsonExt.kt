package com.example.exelgramm.data.remote

import com.example.exelgramm.core.AppError
import kotlinx.serialization.json.Json

inline fun <reified T> Json.decodeApiResponse(body: String): T {
    val trimmed = body.trim()
    if (trimmed.startsWith("<")) throw AppError.HtmlResponse
    return decodeFromString(trimmed)
}
