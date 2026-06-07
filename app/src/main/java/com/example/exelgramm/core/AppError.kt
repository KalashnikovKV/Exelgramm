package com.example.exelgramm.core

/**
 * Типизированные ошибки приложения. Бросаются в data-слое, распознаются в [ErrorTexts].
 */
sealed class AppError : Exception() {
    /** Нет интернета или неверный хост */
    data object NoInternet : AppError()
    /** Сервер вернул HTML вместо JSON (неверный URL /exec или нет публичного доступа) */
    data object HtmlResponse : AppError()
    /** Превышено максимальное количество редиректов */
    data object TooManyRedirects : AppError()
    /** Сервер вернул HTTP-ошибку */
    data class HttpError(val code: Int, val body: String = "") : AppError()
    /** API вернул ok=false с описанием ошибки */
    data class ApiError(val detail: String) : AppError()
    /** Чат не настроен (нет spreadsheetId или webAppUrl) */
    data object ChatNotConfigured : AppError()
}
