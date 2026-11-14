package com.aibuilt.claudeclone.data

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Single place where the stored key is applied to outgoing requests. The request already
 * carries the protocol-specific headers and the final URL, so this only swaps in whatever
 * the person has saved — and does nothing to the auth headers of the other dialect.
 */
class ApiConfigInterceptor(private val settings: SettingsRepository) : Interceptor {

    @Volatile
    var lastError: String? = null
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val saved = settings.current()

        val builder = original.newBuilder()

        if (original.header("x-api-key") != null || original.header("Authorization") != null) {
            // protocol headers were already attached by the builder; just refresh the value
            if (original.header("Authorization") != null) {
                builder.header("Authorization", "Bearer ${saved.effectiveApiKey}")
            } else {
                builder.header("x-api-key", saved.effectiveApiKey)
                builder.header("anthropic-version", ApiDefaults.ANTHROPIC_VERSION)
            }
        } else {
            when (saved.effectiveProtocol) {
                ApiProtocol.ANTHROPIC -> {
                    builder.header("x-api-key", saved.effectiveApiKey)
                    builder.header("anthropic-version", ApiDefaults.ANTHROPIC_VERSION)
                }
                else -> builder.header("Authorization", "Bearer ${saved.effectiveApiKey}")
            }
        }

        return try {
            chain.proceed(builder.build())
        } catch (io: IOException) {
            lastError = io.message
            throw io
        }
    }
}
