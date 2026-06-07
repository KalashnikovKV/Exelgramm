package com.example.exelgramm.data.remote

import com.example.exelgramm.core.AppError
import com.example.exelgramm.domain.model.Message
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AppsScriptApi @Inject constructor(
    @param:Named("noRedirect") private val httpClient: OkHttpClient,
    private val gson: Gson,
) {

    fun fetchMessages(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
    ): Result<List<Message>> = runCatching {
        val body = executeGetChain(webAppUrl, spreadsheetId, sheetName)
        val response = parseJson(body, MessagesResponse::class.java)
        if (!response.ok) throw AppError.ApiError(response.error ?: "Unknown error")
        response.messages.orEmpty().map { it.toDomain() }
    }

    fun sendMessage(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        message: Message,
    ): Result<Unit> = postAction(
        webAppUrl = webAppUrl,
        payload = PostMessageRequest(
            spreadsheetId = spreadsheetId,
            sheet = sheetName,
            id = message.id,
            timestamp = message.timestamp,
            author = message.author,
            text = message.text,
            type = message.type,
        ),
        errorMessage = "Send failed",
    )

    fun updateMessage(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        messageId: String,
        text: String,
    ): Result<Unit> = postAction(
        webAppUrl = webAppUrl,
        payload = UpdateMessageRequest(
            spreadsheetId = spreadsheetId,
            sheet = sheetName,
            id = messageId,
            text = text,
        ),
        errorMessage = "Update failed",
    )

    fun deleteMessage(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        messageId: String,
    ): Result<Unit> = postAction(
        webAppUrl = webAppUrl,
        payload = DeleteMessageRequest(
            spreadsheetId = spreadsheetId,
            sheet = sheetName,
            id = messageId,
        ),
        errorMessage = "Delete failed",
    )

    private fun postAction(webAppUrl: String, payload: Any, errorMessage: String): Result<Unit> =
        runCatching {
            val body = executePost(webAppUrl, gson.toJson(payload))
            val response = parseJson(body, SimpleResponse::class.java)
            if (!response.ok) throw AppError.ApiError(response.error ?: errorMessage)
        }

    private fun executeGetChain(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
    ): String {
        val start = SheetLinkParser.canonicalExecUrl(webAppUrl).toHttpUrl().newBuilder()
            .addQueryParameter("id", spreadsheetId)
            .addQueryParameter("sheet", sheetName)
            .build()
            .toString()
        return followGetRedirects(start)
    }

    private fun followGetRedirects(startUrl: String): String {
        var current = startUrl
        repeat(MAX_HOPS) {
            val request = Request.Builder()
                .url(current)
                .get()
                .header("User-Agent", CHROME_USER_AGENT)
                .header("Accept", "application/json,text/plain,*/*")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (looksLikeJson(body)) return body
                when {
                    response.code in 300..399 -> {
                        current = response.header("Location")
                            ?: throw AppError.HttpError(response.code, "No Location header")
                        return@repeat
                    }
                    response.isSuccessful -> return body
                    else -> throw AppError.HttpError(response.code, body.take(200))
                }
            }
        }
        throw AppError.TooManyRedirects
    }

    /**
     * POST на /exec часто отвечает 302: скрипт уже выполнен, echo-URL отдаёт 405 на POST.
     * Не следуем редиректу POST-ом — считаем успехом 302 или JSON в теле.
     */
    private fun executePost(webAppUrl: String, json: String): String {
        val url = SheetLinkParser.canonicalExecUrl(webAppUrl)
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA))
            .header("User-Agent", CHROME_USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (looksLikeJson(body)) return body
            when {
                response.code in 300..399 -> return """{"ok":true}"""
                response.isSuccessful -> return body.ifBlank { """{"ok":true}""" }
                else -> throw AppError.HttpError(response.code, body.take(200))
            }
        }
    }

    private fun looksLikeJson(body: String): Boolean {
        val t = body.trim()
        return t.startsWith("{") && t.contains("\"ok\"")
    }

    private fun <T> parseJson(body: String, type: Class<T>): T {
        val trimmed = body.trim()
        if (trimmed.startsWith("<")) throw AppError.HtmlResponse
        return gson.fromJson(trimmed, type)
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_HOPS = 8
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
