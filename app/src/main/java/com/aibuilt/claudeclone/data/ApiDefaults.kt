package com.aibuilt.claudeclone.data

object ApiDefaults {
    const val BASE_URL = "https://api.anthropic.com/v1"
    const val API_KEY = "sk-ant-api03-REPLACE-WITH-YOUR-OWN-KEY"
    const val MODEL = "claude-sonnet-4-5"
    const val ANTHROPIC_VERSION = "2023-06-01"
    const val SYSTEM_PROMPT =
        "You are Claude, a helpful, thoughtful AI assistant made by Anthropic. " +
            "Answer clearly and concisely, using plain language and short paragraphs."
}

/** Trims spaces and slashes and adds https:// when the person forgot the scheme. */
fun normalizeBaseUrl(raw: String): String {
    var value = raw.trim()
    if (value.isEmpty()) return ""
    val lower = value.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        value = "https://$value"
    }
    value = value.trimEnd('/')
    return value
}
