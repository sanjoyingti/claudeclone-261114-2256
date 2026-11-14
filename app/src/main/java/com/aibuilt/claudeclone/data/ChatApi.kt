package com.aibuilt.claudeclone.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A failure that already carries its final, human readable text. */
private class ApiHttpException(message: String) : IOException(message)

class ChatApi(private val client: OkHttpClient, private val json: Json) {

    /** Tiny probe used by the settings screen's "Test connection" button. */
    suspend fun testConnection(
        protocol: ApiProtocol,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val settings = ApiSettings(baseUrl, apiKey, model, protocol)
        val normalized = normalizeBaseUrl(baseUrl)
        if (normalized.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a base URL first.")
            )
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter an API key first.")
            )
        }
        if (model.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a model name first.")
            )
        }

        val resolved = settings.effectiveProtocol
        val testClient = client.newBuilder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        val body = buildJsonObject {
            put("model", model.trim())
            put("max_tokens", 8)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "ping")
                }
            }
        }.toString()

        val request = runCatching {
            buildRequest(
                protocol = resolved,
                baseUrl = settings.effectiveBaseUrl,
                apiKey = settings.effectiveApiKey,
                body = body,
                streaming = false
            )
        }.getOrElse { throwable ->
            return@withContext Result.failure(
                IllegalArgumentException(
                    "That base URL can't be used: ${throwable.message.orEmpty()}"
                )
            )
        }

        runCatching {
            testClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiHttpException(errorMessage(response.code, text))
                }
                val preview = extractWholeText(text)
                if (preview != null && preview.startsWith("Server error:", true)) {
                    throw ApiHttpException(preview)
                }
                val label = when (resolved) {
                    ApiProtocol.ANTHROPIC -> "the Anthropic format"
                    else -> "the OpenAI-compatible format"
                }
                val answer = preview?.take(80)?.replace('\n', ' ')?.trim().orEmpty()
                if (answer.isBlank()) {
                    "Connected to ${hostOf(normalized)} using $label."
                } else {
                    "Connected to ${hostOf(normalized)} using $label. Reply: $answer"
                }
            }
        }.recoverCatching { throwable ->
            if (throwable is ApiHttpException) throw throwable
            throw IOException(friendlyMessage(throwable, normalized), throwable)
        }
    }

    /**
     * Streams the reply. Emits text chunks as they arrive and ends when the stream is done.
     * Understands both SSE dialects plus a plain JSON body, so any server works.
     */
    fun streamMessage(
        history: List<ChatMessage>,
        model: String,
        protocol: ApiProtocol,
        baseUrl: String
    ): Flow<String> = flow {
        val messages = JsonArray(
            history.map { message ->
                buildJsonObject {
                    put("role", if (message.isUser) "user" else "assistant")
                    put("content", message.text)
                }
            }
        )
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            put("stream", true)
            put("messages", messages)
        }.toString()

        val request = buildRequest(
            protocol = protocol,
            baseUrl = baseUrl,
            apiKey = "",
            body = body,
            streaming = true
        )

        val response = try {
            client.newCall(request).execute()
        } catch (io: IOException) {
            throw IOException(friendlyMessage(io, baseUrl), io)
        }

        try {
            response.use { result ->
                if (!result.isSuccessful) {
                    val text = result.body?.string().orEmpty()
                    throw ApiHttpException(errorMessage(result.code, text))
                }
                val payload = result.body ?: throw IOException("The server sent an empty body.")
                val contentType = result.header("content-type").orEmpty().lowercase()
                val looksLikeJson = contentType.contains("json")
                val snapshot = runCatching { response.peekBody(1).string() }.getOrNull()
                val isEventStream = contentType.contains("event-stream") ||
                    (!looksLikeJson && !snapshot.isNullOrEmpty())

                if (isEventStream) {
                    val source = payload.source()
                    var produced = false
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        if (data == "[DONE]") break
                        val chunk = parseStreamChunk(data, protocol)
                        if (chunk == STOP) break
                        if (!chunk.isNullOrEmpty()) {
                            produced = true
                            emit(chunk)
                        }
                    }
                    if (!produced) {
                        // server claimed SSE but we read nothing useful — try the plain route
                        val fallback = runCatching { extractWholeText(source.readUtf8()) }.getOrNull()
                        if (!fallback.isNullOrBlank()) emit(fallback)
                    }
                } else {
                    val text = payload.string()
                    val whole = extractWholeText(text)
                    if (whole.isNullOrBlank()) {
                        // a stream served as plain text: split the data lines ourselves
                        var produced = false
                        text.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data:")) {
                                val data = trimmed.removePrefix("data:").trim()
                                if (data.isNotEmpty() && data != "[DONE]") {
                                    val chunk = parseStreamChunk(data, protocol)
                                    if (!chunk.isNullOrEmpty() && chunk != STOP) {
                                        produced = true
                                        emit(chunk)
                                    }
                                }
                            }
                        }
                        if (!produced && text.isNotBlank()) emit(text)
                    } else {
                        emit(whole)
                    }
                }
            }
        } catch (api: ApiHttpException) {
            throw api
        } catch (io: IOException) {
            throw IOException(friendlyMessage(io, baseUrl), io)
        }
    }.flowOn(Dispatchers.IO)

    /** Builds the final request: URL joined without doubling, plus the protocol headers. */
    private fun buildRequest(
        protocol: ApiProtocol,
        baseUrl: String,
        apiKey: String,
        body: String,
        streaming: Boolean
    ): Request {
        val url = joinUrl(baseUrl, protocol)
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("content-type", "application/json")
            .header("accept", if (streaming) "text/event-stream" else "application/json")

        when (protocol) {
            ApiProtocol.ANTHROPIC -> {
                builder.header("x-api-key", apiKey.ifBlank { ApiDefaults.API_KEY })
                builder.header("anthropic-version", ApiDefaults.ANTHROPIC_VERSION)
            }
            else -> {
                builder.header("Authorization", "Bearer ${apiKey.ifBlank { ApiDefaults.API_KEY }}")
            }
        }
        return builder.build()
    }

    /**
     * Joins the base and the protocol path. A base that already ends in either endpoint or in
     * /v1 is respected so the path is never doubled, and a base saved for the other dialect is
     * switched over to the endpoint this request needs.
     */
    private fun joinUrl(baseUrl: String, protocol: ApiProtocol): String {
        var base = normalizeBaseUrl(baseUrl)
        if (base.isBlank()) base = ApiDefaults.BASE_URL
        val lower = base.lowercase()
        val path = when (protocol) {
            ApiProtocol.ANTHROPIC -> "messages"
            else -> "chat/completions"
        }

        val endsWithOpenAi = lower.endsWith("/chat/completions")
        val endsWithAnthropic = lower.endsWith("/messages")
        if (endsWithOpenAi || endsWithAnthropic) {
            val prefix = base.substringBeforeLast('/')
            if (!prefix.contains("://") || prefix.length <= prefix.indexOf("://") + 3) {
                return "$prefix/$path"
            }
            return "$prefix/$path"
        }
        if (lower.endsWith("/${ApiDefaults.VERSION_SEGMENT}")) return "$base/$path"
        return "$base/${ApiDefaults.VERSION_SEGMENT}/$path"
    }

    /** Reads one SSE data payload and returns the text it carries, STOP, or null. */
    private fun parseStreamChunk(data: String, protocol: ApiProtocol): String? {
        val element = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null

        val anthropicType = obj["type"]?.jsonPrimitive?.contentOrNull
        if (anthropicType == "message_stop") return STOP
        if (anthropicType != null) {
            val delta = obj["delta"] as? JsonObject
            val text = delta?.get("text")?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrEmpty()) return text
            val blockDelta = obj["content_block_delta"] as? JsonObject
            val blockText = blockDelta?.get("text")?.jsonPrimitive?.contentOrNull
            if (!blockText.isNullOrEmpty()) return blockText
            val error = obj["error"] as? JsonObject
            val errorText = error?.get("message")?.jsonPrimitive?.contentOrNull
            if (!errorText.isNullOrEmpty()) return "Server error: $errorText"
        }

        val choices = obj["choices"] as? JsonArray
        val first = choices?.firstOrNull() as? JsonObject
        if (first != null) {
            val delta = first["delta"] as? JsonObject
            val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
            if (!content.isNullOrEmpty()) return content
            val message = first["message"] as? JsonObject
            val messageContent = message?.get("content")?.jsonPrimitive?.contentOrNull
            if (!messageContent.isNullOrEmpty()) return messageContent
            val finish = first["finish_reason"]?.jsonPrimitive?.contentOrNull
            if (finish != null && finish != "null") return null
        }

        // some proxies wrap plain text in a bare field
        val plain = obj["completion"]?.jsonPrimitive?.contentOrNull
            ?: obj["text"]?.jsonPrimitive?.contentOrNull
            ?: obj["response"]?.jsonPrimitive?.contentOrNull
        if (!plain.isNullOrEmpty()) return plain

        // whole-message payloads (non streaming JSON inside an SSE frame)
        val whole = extractWholeText(data)
        return whole
    }

    /** Pulls the full text out of a plain, non-streamed JSON body. */
    private fun extractWholeText(body: String): String? {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null

        val choices = obj["choices"] as? JsonArray
        val firstChoice = choices?.firstOrNull() as? JsonObject
        val fromChoice = firstChoice?.get("message")
            ?.let { it as? JsonObject }
            ?.get("content")
            ?.let(::textOf)
        if (!fromChoice.isNullOrBlank()) return fromChoice

        val fromDelta = firstChoice?.get("delta")
            ?.let { it as? JsonObject }
            ?.get("content")
            ?.let(::textOf)
        if (!fromDelta.isNullOrBlank()) return fromDelta

        val content = obj["content"]
        val fromContent = content?.let(::textOf)
        if (!fromContent.isNullOrBlank()) return fromContent

        obj["error"]?.let { error ->
            val message = (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            if (!message.isNullOrBlank()) return "Server error: $message"
        }
        return null
    }

    private fun textOf(element: JsonElement): String? = when (element) {
        is JsonArray -> element.mapNotNull { block ->
            val obj = block as? JsonObject
            val text = obj?.get("text")?.jsonPrimitive?.contentOrNull
            text
        }.joinToString("").ifBlank { null }
        else -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
    }

    private fun errorMessage(code: Int, body: String): String {
        val parsed = runCatching {
            val element = json.parseToJsonElement(body)
            val obj = element.jsonObject
            val errorObj = obj["error"] as? JsonObject
            errorObj?.get("message")?.jsonPrimitive?.contentOrNull
                ?: obj["message"]?.jsonPrimitive?.contentOrNull
                ?: errorObj?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        val trimThreshold = 300
        var detail = parsed?.trim().orEmpty()
        if (detail.isBlank()) {
            detail = body.trim().replace(Regex("\\s+"), " ")
            if (detail.length > trimThreshold) detail = detail.take(trimThreshold) + "…"
        }
        if (detail.isBlank()) detail = "no details"

        return when (code) {
            400 -> "Bad request (400): $detail"
            401 -> "Wrong API key (401): $detail"
            403 -> "Access denied (403): $detail"
            404 -> "Not found (404): check the base URL and the model name. $detail"
            422 -> "Model or body rejected (422): $detail"
            429 -> "Rate limited (429): $detail"
            in 500..599 -> "Server error ($code): $detail"
            else -> "Request failed ($code): $detail"
        }
    }

    private fun friendlyMessage(throwable: Throwable, baseUrl: String): String {
        val host = hostOf(baseUrl).ifBlank { baseUrl }
        val text = throwable.message.orEmpty()
        return when {
            text.startsWith("Wrong API key") || text.startsWith("Access denied") ||
                text.startsWith("Not found") || text.startsWith("Rate limited") ||
                text.startsWith("Bad request") || text.startsWith("Model or body") ||
                text.startsWith("Server error") || text.startsWith("Request failed") -> text
            throwable is UnknownHostException || text.contains("Unable to resolve host", true) ->
                "Cannot reach $host — check the address or the connection."
            throwable is SocketTimeoutException || text.contains("timeout", true) ->
                "$host did not answer in time."
            throwable is ConnectException || text.contains("Failed to connect", true) ->
                "Could not connect to $host."
            text.contains("CLEARTEXT", true) -> "Plain http:// was blocked. Try https://"
            throwable is IOException && text.isNotBlank() -> "$host: $text"
            else -> throwable.localizedMessage ?: "Unknown network error."
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val STOP = "\u0000stop"
    }
}
