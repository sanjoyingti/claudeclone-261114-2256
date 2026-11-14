package com.aibuilt.claudeclone.data

/**
 * Built-in fallback endpoint. Used only when the person leaves a field empty, so the app
 * still starts out working — but nothing in the request path depends on it any more.
 */
object ApiDefaults {
    const val BASE_URL = "https://api.anthropic.com"
    const val API_KEY = "sk-ant-api03-REPLACE-WITH-YOUR-OWN-KEY"
    const val MODEL = "claude-sonnet-4-5"
    const val ANTHROPIC_VERSION = "2023-06-01"

    /** Path fragment of the version folder, used to join paths without doubling it. */
    const val VERSION_SEGMENT = "v1"
}

enum class ApiProtocol {
    AUTO,
    ANTHROPIC,
    OPENAI
}

/**
 * Decides which wire format to speak. Manual choice always wins; otherwise the URL shape is
 * inspected: anything that smells like Anthropic uses the Anthropic format, everything else
 * (OpenAI, Groq, OpenRouter, Ollama, LM Studio, LocalAI, proxies, unknown) is OpenAI-compatible.
 */
fun resolveProtocol(selected: ApiProtocol, baseUrl: String): ApiProtocol {
    if (selected != ApiProtocol.AUTO) return selected
    val url = normalizeBaseUrl(baseUrl).lowercase()
    return when {
        url.contains("anthropic") -> ApiProtocol.ANTHROPIC
        url.contains("/messages") -> ApiProtocol.ANTHROPIC
        url.contains("chat/completions") -> ApiProtocol.OPENAI
        url.contains("openai") -> ApiProtocol.OPENAI
        url.contains("groq") -> ApiProtocol.OPENAI
        url.contains("openrouter") -> ApiProtocol.OPENAI
        url.contains("ollama") || url.contains("llama") -> ApiProtocol.OPENAI
        url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2") ->
            ApiProtocol.OPENAI
        else -> ApiProtocol.OPENAI
    }
}

/** Trims spaces and slashes and adds https:// when the person forgot the scheme. */
fun normalizeBaseUrl(raw: String): String {
    var value = raw.trim()
    if (value.isEmpty()) return ""
    val lower = value.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        value = "https://$value"
    }
    while (value.endsWith('/')) {
        value = value.dropLast(1)
    }
    return value
}

/** Host of an address, for friendly error text. */
fun hostOf(raw: String): String {
    val normalized = normalizeBaseUrl(raw)
    if (normalized.isBlank()) return ""
    return runCatching { java.net.URI(normalized).host.orEmpty() }.getOrDefault("")
}
