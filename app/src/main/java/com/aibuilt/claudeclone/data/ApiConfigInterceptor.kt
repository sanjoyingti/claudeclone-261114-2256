package com.aibuilt.claudeclone.data

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Single place where the stored address and key are applied to outgoing requests, so the
 * rest of the app talks to the plain default endpoint and never worries about settings.
 */
class ApiConfigInterceptor(private val settings: SettingsRepository) : Interceptor {

    @Volatile
    var lastError: String? = null
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val baseUrl = settings.requestBaseUrl()
        val apiKey = settings.requestApiKey()

        val newUrl = rewriteUrl(original.url, baseUrl)
        val builder: Request.Builder = original.newBuilder()
            .url(newUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ApiDefaults.ANTHROPIC_VERSION)
            .header("anthropic-dangerous-direct-browser-access", "true")

        return try {
            chain.proceed(builder.build())
        } catch (io: IOException) {
            lastError = io.message
            throw io
        }
    }

    /**
     * Puts the request path onto the configured base. The built-in prefix (for example
     * "/v1") is dropped from the request first, so a base that already contains a version
     * folder or a proxy prefix does not end up doubled.
     */
    private fun rewriteUrl(original: HttpUrl, baseUrl: String): HttpUrl {
        val base = baseUrl.toHttpUrlOrNullSafe() ?: return original
        val basePath = base.encodedPath.trimEnd('/')
        var remaining = original.encodedPath
        if (basePath.isNotEmpty() && remaining.startsWith(basePath)) {
            remaining = remaining.removePrefix(basePath)
        } else if (DEFAULT_PATH_PREFIX.isNotEmpty() && remaining.startsWith(DEFAULT_PATH_PREFIX)) {
            remaining = remaining.removePrefix(DEFAULT_PATH_PREFIX)
        }
        val builder = base.newBuilder()
        remaining.split('/')
            .filter { it.isNotEmpty() }
            .forEach { builder.addPathSegment(it) }
        original.queryParameterNames.forEach { name ->
            original.queryParameterValues(name).forEach { value ->
                if (value != null) builder.addQueryParameter(name, value)
            }
        }
        return builder.build()
    }

    private companion object {
        val DEFAULT_PATH_PREFIX: String =
            ApiDefaults.BASE_URL.toHttpUrlOrNull()?.encodedPath?.trimEnd('/').orEmpty()
    }
}

private fun String.toHttpUrlOrNullSafe(): HttpUrl? {
    val normalized = normalizeBaseUrl(this)
    if (normalized.isBlank()) return null
    return normalized.toHttpUrlOrNull()
}
