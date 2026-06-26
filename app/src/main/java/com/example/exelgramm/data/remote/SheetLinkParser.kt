package com.example.exelgramm.data.remote

object SheetLinkParser {

    private val SPREADSHEET_ID_REGEX =
        Regex("""/spreadsheets/d/([a-zA-Z0-9-_]+)""")

    private val DEPLOYMENT_ID_REGEX =
        Regex("""/macros/s/([a-zA-Z0-9-_]+)""")

    fun parseSpreadsheetId(url: String): String? =
        SPREADSHEET_ID_REGEX.find(url.trim())?.groupValues?.getOrNull(1)

    /** Deploy URL: https://script.google.com/macros/s/ID/exec */
    fun canonicalExecUrl(url: String): String {
        val trimmed = url.trim()
        val deploymentId = DEPLOYMENT_ID_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
        if (deploymentId != null) {
            return "https://script.google.com/macros/s/$deploymentId/exec"
        }
        if (trimmed.contains("script.googleusercontent.com")) {
            return trimmed
        }
        return normalizeWebAppUrl(trimmed)
    }

    fun normalizeWebAppUrl(url: String): String = canonicalExecUrl(url)
}
