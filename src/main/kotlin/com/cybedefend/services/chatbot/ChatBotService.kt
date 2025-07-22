// File: src/main/kotlin/com/cybedefend/services/ChatBotService.kt
package com.cybedefend.services

import AddMessageConversationRequestDto
import StartConversationRequestDto
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource

/** Manages conversation lifecycle and SSE streaming for AI chat. */
class ChatBotService(private val apiService: ApiService) {
    private val client = OkHttpClient()

    /** Starts a new conversation, returns its ID. */
    suspend fun startConversation(request: StartConversationRequestDto): String =
            apiService.startConversation(request.projectId!!, request = request).conversationId

    /** Continues an existing conversation, returns its ID. */
    suspend fun continueConversation(
            conversationId: String,
            request: AddMessageConversationRequestDto
    ): String =
            apiService.continueConversation(request.projectId, conversationId, request = request)
                    .conversationId

    /**
     * Streams SSE updates for a conversation. Calls onDelta for each delta payload. onComplete when
     * stream ends, onError on failure.
     */
    suspend fun streamConversation(
            projectId: String,
            conversationId: String,
            initialMessage: String? = null,
            onDelta: (String) -> Unit,
            onComplete: () -> Unit,
            onError: (Throwable) -> Unit
    ) =
            withContext(Dispatchers.IO) {
                val baseUrl = apiService.baseUrl.trimEnd('/')
                val url = buildString {
                    append("$baseUrl/project/$projectId/ai/conversation/$conversationId/stream")
                    if (!initialMessage.isNullOrBlank()) {
                        append("?message=${URLEncoder.encode(initialMessage, "UTF-8")}")
                    }
                }

                val request =
                        Request.Builder()
                                .url(url)
                                .addHeader(
                                        "X-API-Key",
                                        apiService.authService.getApiKey().orEmpty()
                                )
                                .build()

                var response: Response? = null
                try {
                    response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw IOException("SSE HTTP ${response.code}")
                    val source: BufferedSource =
                            response.body?.source() ?: throw IOException("Empty SSE body")

                    val buffer = StringBuilder()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) {
                            // On n’essaie de parser que ce qu’on a accumulé
                            val raw = buffer.toString().trim()
                            buffer.clear()
                            if (raw == "null" || raw.isBlank()) continue
                            val reader = JsonReader(StringReader(raw)).apply { isLenient = true }
                            val obj = JsonParser.parseReader(reader).asJsonObject
                            when (obj.get("type").asString) {
                                "delta" -> onDelta(obj.get("payload").asString)
                                "done" -> {
                                    onComplete()
                                    break
                                }
                                "error" ->
                                        throw IOException(
                                                obj.getAsJsonObject("payload")
                                                        .get("message")
                                                        .asString
                                        )
                                else -> Unit
                            }
                        } else if (line.startsWith("data:")) {
                            // On n’accumule que les lignes data:
                            buffer.append(line.removePrefix("data:")).append("\n")
                        }
                    }
                } catch (t: Throwable) {
                    onError(t)
                } finally {
                    response?.close()
                }
            }
}
