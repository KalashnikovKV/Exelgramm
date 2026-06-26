package com.example.exelgramm.core

/**
 * Typed app errors. Thrown in the data layer, mapped in [ErrorTexts].
 */
sealed class AppError : Exception() {
    /** No internet or invalid host */
    data object NoInternet : AppError()
    /** Server returned HTML instead of JSON (bad /exec URL or no public access) */
    data object HtmlResponse : AppError()
    /** Too many redirects */
    data object TooManyRedirects : AppError()
    /** HTTP error from server */
    data class HttpError(val code: Int, val body: String = "") : AppError()
    /** API returned ok=false */
    data class ApiError(val detail: String) : AppError()
    /** Chat not configured (missing spreadsheetId or webAppUrl) */
    data object ChatNotConfigured : AppError()
}
