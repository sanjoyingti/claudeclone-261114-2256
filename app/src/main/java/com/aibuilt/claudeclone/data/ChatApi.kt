package com.aibuilt.claudeclone.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatApi(private val client: OkHttpClient, private val json: Json) {

    /** Tiny probe used by the settings screen's "Test connection" button. */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
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
            val testClient = client.newBuilder()
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            val body = buildJsonObject {
                put("model", ApiDefaults.MODEL)
                put("max_tokens", 8)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", "ping")
                    }
                }
            }.toString()

            val request = Request.Builder()
                .url("$normalized/messages")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            runCatching {
                testClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException(errorMessage(response.code, text))
                    }
                    val reply = runCatching {
                        json.parseToJsonElement(text)
                            .jsonObject["content"]
                            ?.jsonArray
                            ?.firstOrNull()
                            ?.jsonObject
                            ?.get("text")
                            ?.jsonPrimitive
                            ?.contentOrNull
                    }.getOrNull()
                    val model = runCatching {
                        json.parseToJsonElement(text)
                            .jsonObject["model"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                    }.getOrNull()
                    if (reply != null || model != null) {
                        "Connected successfully to $normalized"
                    } else {
                        "Connected to $normalized"
                    }
                }
            }.recoverCatching { throwable ->
                throw IOException(friendlyMessage(throwable), throwable)
            }
        }

    /** Sends the whole conversation and returns Claude's reply text. */
    suspend fun sendMessage(history: List<ChatMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            val messages = JsonArray(
                history.map { message ->
                    buildJsonObject {
                        put("role", if (message.isUser) "user" else "assistant")
                        put("content", message.text)
                    }
                }
            )
            val body = buildJsonObject {
                put("model", ApiDefaults.MODEL)
                put("max_tokens", 1024)
                put("system", ApiDefaults.SYSTEM_PROMPT)
                put("stream", false)
                put("messages", messages)
            }.toString()

            val request = Request.Builder()
                .url("${ApiDefaults.BASE_URL}/messages")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException(errorMessage(response.code, text))
                    }
                    val reply = json.parseToJsonElement(text)
                        .jsonObject["content"]
                        ?.jsonArray
                        ?.mapNotNull { block ->
                            val obj: JsonObject = block.jsonObject
                            if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                                obj["text"]?.jsonPrimitive?.contentOrNull
                            } else {
                                null
                            }
                        }
                        ?.joinToString("\n")
                        .orEmpty()
                    if (reply.isBlank()) {
                        "Claude replied but sent no text."
                    } else {
                        reply
                    }
                }
            }.recoverCatching { throwable ->
                throw IOException(friendlyMessage(throwable), throwable)
            }
        }

    private fun errorMessage(code: Int, body: String): String {
        val parsed = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
        val detail = parsed?.takeIf { it.isNotBlank() } ?: body.take(300).ifBlank { "no details" }
        return when (code) {
            401, 403 -> "Authentication failed ($code): $detail"
            404 -> "Not found ($code): check the base URL. $detail"
            429 -> "Rate limited ($code): $detail"
            else -> "Request failed ($code): $detail"
        }
    }

    private fun friendlyMessage(throwable: Throwable): String {
        val text = throwable.message.orEmpty()
        return when {
            text.contains("Unable to resolve host", true) -> "Cannot reach that host. Check the URL."
            text.contains("timeout", true) -> "Connection timed out."
            text.contains("Failed to connect", true) -> "Could not connect to that address."
            text.contains("CLEARTEXT", true) -> "Plain http:// was blocked. Try https://"
            throwable is IOException && text.isNotBlank() -> text
            else -> throwable.localizedMessage ?: "Unknown network error."
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
