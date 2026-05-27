package com.example.exelgramm.data.repository

import androidx.annotation.StringRes
import com.example.exelgramm.R
import com.example.exelgramm.data.remote.SheetLinkParser

/**
 * Валидирует поля конфигурации чата, разделяя бизнес-правила от UI.
 * Устраняет дублирование: один вызов validate() заменяет набор Toast-проверок в нескольких местах.
 */
object ChatConfigValidator {

    sealed interface Result {
        /** Поля прошли валидацию; [spreadsheetId] уже распарсен из sheetUrl. */
        data class Success(val spreadsheetId: String) : Result
        data class Failure(@param:StringRes val errorResId: Int) : Result
    }

    fun validate(sheetUrl: String, webAppUrl: String): Result {
        val spreadsheetId = SheetLinkParser.parseSpreadsheetId(sheetUrl)
            ?: return Result.Failure(R.string.error_invalid_sheet_url)
        if (webAppUrl.isBlank())
            return Result.Failure(R.string.error_web_app_url_required)
        if (!webAppUrl.contains("script.google.com/macros/s/"))
            return Result.Failure(R.string.error_web_app_must_be_exec)
        return Result.Success(spreadsheetId)
    }
}
