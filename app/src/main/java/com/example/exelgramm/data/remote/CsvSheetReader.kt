package com.example.exelgramm.data.remote

import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Запасное чтение через публичный CSV (если таблица доступна «все с ссылкой — просмотр»).
 */
object CsvSheetReader {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetch(spreadsheetId: String, sheetName: String): Result<List<Message>> = runCatching {
        val sheet = URLEncoder.encode(sheetName, Charsets.UTF_8.name())
        val url =
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&sheet=$sheet"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Exelgramm/1.0 (Android)")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.trimStart().startsWith("<")) {
                throw IllegalStateException("CSV недоступен (нужен доступ «просмотр» по ссылке)")
            }
            parseCsv(body)
        }
    }

    private fun parseCsv(csv: String): List<Message> {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val headers = parseRow(lines.first()).map { it.lowercase() }
        val textIdx = headers.indexOf("text").takeIf { it >= 0 } ?: 3
        val idIdx = headers.indexOf("id").takeIf { it >= 0 } ?: 0
        val timeIdx = headers.indexOf("timestamp").takeIf { it >= 0 } ?: 1
        val authorIdx = headers.indexOf("author").takeIf { it >= 0 } ?: 2
        val typeIdx = headers.indexOf("type").takeIf { it >= 0 } ?: -1

        return lines.drop(1).mapNotNull { line ->
            val cols = parseRow(line)
            val text = cols.getOrNull(textIdx)?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val type = if (typeIdx >= 0) cols.getOrNull(typeIdx)?.trim().orEmpty().ifBlank { MessageType.TEXT }
                       else MessageType.TEXT
            Message(
                id = cols.getOrNull(idIdx)?.trim().orEmpty().ifBlank { "row_${line.hashCode()}" },
                timestamp = cols.getOrNull(timeIdx)?.trim().orEmpty(),
                author = cols.getOrNull(authorIdx)?.trim().orEmpty().ifBlank { "unknown" },
                text = text,
                type = type,
            )
        }
    }

    private fun parseRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }
}
