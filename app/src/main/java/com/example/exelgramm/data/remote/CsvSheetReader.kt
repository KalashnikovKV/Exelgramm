package com.example.exelgramm.data.remote

import com.example.exelgramm.core.runSuspendCatchingCancellable
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Запасное чтение через публичный CSV (если таблица доступна «все с ссылкой — просмотр»).
 */
@Singleton
class CsvSheetReader @Inject constructor(
    @param:Named("default") private val httpClient: OkHttpClient,
) : CsvMessagesClient {

    override suspend fun fetch(spreadsheetId: String, sheetName: String): Result<List<Message>> = runSuspendCatchingCancellable {
        val sheet = URLEncoder.encode(sheetName, Charsets.UTF_8.name())
        val url =
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&sheet=$sheet"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Exelgramm/1.0 (Android)")
            .build()
        httpClient.newCall(request).await().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.trimStart().startsWith("<")) {
                throw IllegalStateException("CSV недоступен (нужен доступ «просмотр» по ссылке)")
            }
            parseCsv(body)
        }
    }

    private fun parseCsv(csv: String): List<Message> {
        val rows = parseCsvToRows(csv).filter { row -> row.any { it.isNotBlank() } }
        if (rows.size < 2) return emptyList()

        val headers = rows.first().map { it.trim().lowercase() }
        val idIdx    = headers.indexOf("id").takeIf { it >= 0 } ?: 0
        val timeIdx  = headers.indexOf("timestamp").takeIf { it >= 0 } ?: 1
        val authorIdx = headers.indexOf("author").takeIf { it >= 0 } ?: 2
        val textIdx  = headers.indexOf("text").takeIf { it >= 0 } ?: 3
        val typeIdx  = headers.indexOf("type").takeIf { it >= 0 } ?: -1

        return rows.drop(1).mapIndexedNotNull { rowIndex, cols ->
            val text = cols.getOrNull(textIdx)?.trim().orEmpty()
            if (text.isEmpty()) return@mapIndexedNotNull null
            val type = if (typeIdx >= 0) {
                MessageType.fromString(cols.getOrNull(typeIdx)?.trim().orEmpty())
            } else {
                MessageType.TEXT
            }
            Message(
                id = cols.getOrNull(idIdx)?.trim().orEmpty()
                    .ifBlank { stableCsvRowId(rowIndex + 2, cols) },
                timestamp = cols.getOrNull(timeIdx)?.trim().orEmpty(),
                author = cols.getOrNull(authorIdx)?.trim().orEmpty().ifBlank { "unknown" },
                text = text,
                type = type,
            )
        }
    }

    /**
     * RFC 4180-совместимый парсер CSV.
     *
     * Поддерживает:
     * - переносы строк внутри кавычек (многострочные поля)
     * - экранированные кавычки через "" → "
     * - CRLF и LF как разделители строк
     */
    private fun parseCsvToRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> {
                    row.add(sb.toString())
                    sb.clear()
                }
                !inQuotes && c == '\n' -> {
                    row.add(sb.toString())
                    rows.add(row.toList())
                    row.clear()
                    sb.clear()
                }
                !inQuotes && c == '\r' -> {
                    if (text.getOrNull(i + 1) == '\n') i++
                    row.add(sb.toString())
                    rows.add(row.toList())
                    row.clear()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row.toList())
        }
        return rows
    }
}

/**
 * Стабильный ID для строк CSV без колонки id: номер строки листа + SHA-256 содержимого.
 * rowNumber — 1-based номер строки в таблице (строка 1 = заголовок).
 */
internal fun stableCsvRowId(rowNumber: Int, cols: List<String>): String {
    val payload = cols.joinToString("\u0001")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(payload.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "csv_${rowNumber}_$digest"
}
