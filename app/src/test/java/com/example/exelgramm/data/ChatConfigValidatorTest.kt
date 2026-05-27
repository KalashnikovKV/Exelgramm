package com.example.exelgramm.data

import com.example.exelgramm.R
import com.example.exelgramm.data.repository.ChatConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatConfigValidatorTest {

    private val validSheetUrl =
        "https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms/edit#gid=0"
    private val validWebAppUrl =
        "https://script.google.com/macros/s/AKfycbxABC123XYZ/exec"

    @Test
    fun `valid inputs return Success with correct spreadsheetId`() {
        val result = ChatConfigValidator.validate(validSheetUrl, validWebAppUrl)
        assert(result is ChatConfigValidator.Result.Success)
        assertEquals(
            "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms",
            (result as ChatConfigValidator.Result.Success).spreadsheetId,
        )
    }

    @Test
    fun `invalid sheet URL returns error_invalid_sheet_url`() {
        val result = ChatConfigValidator.validate("not-a-url", validWebAppUrl)
        assert(result is ChatConfigValidator.Result.Failure)
        assertEquals(
            R.string.error_invalid_sheet_url,
            (result as ChatConfigValidator.Result.Failure).errorResId,
        )
    }

    @Test
    fun `blank webAppUrl returns error_web_app_url_required`() {
        val result = ChatConfigValidator.validate(validSheetUrl, "")
        assert(result is ChatConfigValidator.Result.Failure)
        assertEquals(
            R.string.error_web_app_url_required,
            (result as ChatConfigValidator.Result.Failure).errorResId,
        )
    }

    @Test
    fun `non-exec webAppUrl returns error_web_app_must_be_exec`() {
        val result = ChatConfigValidator.validate(validSheetUrl, "https://example.com/not-exec")
        assert(result is ChatConfigValidator.Result.Failure)
        assertEquals(
            R.string.error_web_app_must_be_exec,
            (result as ChatConfigValidator.Result.Failure).errorResId,
        )
    }
}
