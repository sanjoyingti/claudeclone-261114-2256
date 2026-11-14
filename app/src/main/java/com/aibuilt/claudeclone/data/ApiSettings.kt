package com.aibuilt.claudeclone.data

data class ApiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val protocol: ApiProtocol = ApiProtocol.AUTO
) {
    /** The address a request should really go to, with the built-in endpoint as fallback. */
    val effectiveBaseUrl: String get() = normalizeBaseUrl(baseUrl).ifBlank { ApiDefaults.BASE_URL }

    /** The key a request should really send, with the built-in key as fallback. */
    val effectiveApiKey: String get() = apiKey.trim().ifBlank { ApiDefaults.API_KEY }

    /** The model a request should really ask for, with the built-in model as fallback. */
    val effectiveModel: String get() = model.trim().ifBlank { ApiDefaults.MODEL }

    val effectiveProtocol: ApiProtocol get() = resolveProtocol(protocol, effectiveBaseUrl)
}
