package com.example.exelgramm.data.remote

import com.example.exelgramm.core.AppError
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.core.runSuspendCatchingCancellable
import com.example.exelgramm.domain.model.Message
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AppsScriptApi @Inject constructor(
    @param:Named("noRedirect") private val httpClient: OkHttpClient,
    private val json: Json,
) : MessagesApiClient {

    override suspend fun fetchMessages(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        since: String?,
    ): Result<List<Message>> = runSuspendCatchingCancellable {
        val body = executeGetChain(webAppUrl, spreadsheetId, sheetName, since)
        val response = json.decodeApiResponse<MessagesResponse>(body)
        if (!response.ok) throw AppError.ApiError(response.error ?: "Unknown error")
        response.messages.orEmpty().map { it.toDomain() }
    }

    override suspend fun sendMessage(
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
            timestamp = TimeFormats.toIso(message.timestamp),
            author = message.author,
            text = message.text,
            type = message.type.apiValue,
        ),
        errorMessage = "Send failed",
    )

    override suspend fun updateMessage(
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

    override suspend fun deleteMessage(
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

    private suspend fun postAction(webAppUrl: String, payload: Any, errorMessage: String): Result<Unit> =
        runSuspendCatchingCancellable {
            val jsonBody = when (payload) {
                is PostMessageRequest -> json.encodeToString(PostMessageRequest.serializer(), payload)
                is UpdateMessageRequest -> json.encodeToString(UpdateMessageRequest.serializer(), payload)
                is DeleteMessageRequest -> json.encodeToString(DeleteMessageRequest.serializer(), payload)
                else -> error("Unsupported payload type")
            }
            val body = executePost(webAppUrl, jsonBody)
            val response = json.decodeApiResponse<SimpleResponse>(body)
            if (!response.ok) throw AppError.ApiError(response.error ?: errorMessage)
        }

    private suspend fun executeGetChain(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        since: String?,
    ): String {
        val builder = SheetLinkParser.canonicalExecUrl(webAppUrl).toHttpUrl().newBuilder()
            .addQueryParameter("id", spreadsheetId)
            .addQueryParameter("sheet", sheetName)
        if (!since.isNullOrBlank()) {
            builder.addQueryParameter("since", since)
        }
        return followGetRedirects(builder.build().toString())
    }

    private suspend fun followGetRedirects(startUrl: String): String {
        var current = startUrl
        repeat(MAX_HOPS) {
            val request = Request.Builder()
                .url(current)
                .get()
                .header("User-Agent", CHROME_USER_AGENT)
                .header("Accept", "application/json,text/plain,*/*")
                .build()
            httpClient.newCall(request).await().use { response ->
                when {
                    response.isJson() -> return response.body?.string().orEmpty()
                    response.code in 300..399 -> {
                        current = response.header("Location")
                            ?: throw AppError.HttpError(response.code, "No Location header")
                        return@repeat
                    }
                    response.isSuccessful -> return response.body?.string().orEmpty()
                    else -> throw AppError.HttpError(response.code, response.body?.string().orEmpty().take(200))
                }
            }
        }
        throw AppError.TooManyRedirects
    }

    private suspend fun executePost(webAppUrl: String, jsonBody: String): String {
        val url = SheetLinkParser.canonicalExecUrl(webAppUrl)
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .header("User-Agent", CHROME_USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
        httpClient.newCall(request).await().use { response ->
            when {
                response.isJson() -> return response.body?.string().orEmpty()
                response.code in 300..399 -> return """{"ok":true}"""
                response.isSuccessful -> return response.body?.string().orEmpty().ifBlank { """{"ok":true}""" }
                else -> throw AppError.HttpError(response.code, response.body?.string().orEmpty().take(200))
            }
        }
    }

    private fun okhttp3.Response.isJson(): Boolean =
        header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_HOPS = 8
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
