package com.example.exelgramm.data.repository

import androidx.annotation.StringRes
import com.example.exelgramm.core.DEFAULT_SHEET_NAME
import com.example.exelgramm.data.local.SessionStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveChatConfigUseCase @Inject constructor(
    private val sessionStore: SessionStore,
) {

    sealed interface Result {
        data object Success : Result
        data class ValidationError(@param:StringRes val errorResId: Int) : Result
    }

    suspend operator fun invoke(
        sheetUrl: String,
        webAppUrl: String,
        sheetName: String,
    ): Result = when (val validation = ChatConfigValidator.validate(sheetUrl, webAppUrl)) {
        is ChatConfigValidator.Result.Failure ->
            Result.ValidationError(validation.errorResId)

        is ChatConfigValidator.Result.Success -> {
            sessionStore.saveChatConfig(
                sheetUrl = sheetUrl,
                spreadsheetId = validation.spreadsheetId,
                sheetName = sheetName.ifBlank { DEFAULT_SHEET_NAME },
                webAppUrl = webAppUrl,
            )
            Result.Success
        }
    }
}
