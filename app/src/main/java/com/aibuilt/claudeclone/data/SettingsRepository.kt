package com.aibuilt.claudeclone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.settingsDataStore by preferencesDataStore(name = "claude_settings")

private val BASE_URL_KEY = stringPreferencesKey("base_url")
private val MODEL_KEY = stringPreferencesKey("model")
private val PROTOCOL_KEY = stringPreferencesKey("protocol")

private const val PREFS_NAME = "claude_secure_settings"
private const val FALLBACK_PREFS_NAME = "claude_secure_settings_fallback"
private const val API_KEY_PREF = "api_key"

class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.settingsDataStore

    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            appContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val _state = MutableStateFlow(ApiSettings())
    val state: StateFlow<ApiSettings> = _state.asStateFlow()

    /** Temporary values used while a connection test is running. */
    @Volatile
    var override: ApiSettings? = null

    init {
        scope.launch {
            val stored = runCatching {
                dataStore.data.map { prefs ->
                    Triple(
                        prefs[BASE_URL_KEY] ?: "",
                        prefs[MODEL_KEY] ?: "",
                        prefs[PROTOCOL_KEY] ?: ""
                    )
                }.first()
            }.getOrDefault(Triple("", "", ""))
            val storedKey = withContext(Dispatchers.IO) {
                runCatching { prefs.getString(API_KEY_PREF, "").orEmpty() }.getOrDefault("")
            }
            _state.value = ApiSettings(
                baseUrl = stored.first,
                apiKey = storedKey,
                model = stored.second,
                protocol = parseProtocol(stored.third)
            )
        }
    }

    suspend fun save(rawBaseUrl: String, rawApiKey: String, rawModel: String, protocol: ApiProtocol) {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        val apiKey = rawApiKey.trim()
        val model = rawModel.trim()
        withContext(Dispatchers.IO) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[BASE_URL_KEY] = baseUrl
                    prefs[MODEL_KEY] = model
                    prefs[PROTOCOL_KEY] = protocol.name
                }
            }
            runCatching { prefs.edit().putString(API_KEY_PREF, apiKey).apply() }
        }
        _state.value = ApiSettings(baseUrl, apiKey, model, protocol)
    }

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            runCatching { dataStore.edit { it.clear() } }
            runCatching { prefs.edit().remove(API_KEY_PREF).apply() }
        }
        _state.value = ApiSettings()
    }

    /** The values actually used for a request, ready to build it from. */
    fun current(): ApiSettings = override ?: _state.value.apply {
        // make sure the fallbacks are applied to a copy, never to stored values
    }.let { stored ->
        ApiSettings(
            baseUrl = stored.baseUrl,
            apiKey = stored.apiKey,
            model = stored.model,
            protocol = stored.protocol
        )
    }

    private fun parseProtocol(raw: String): ApiProtocol =
        runCatching { ApiProtocol.valueOf(raw) }.getOrDefault(ApiProtocol.AUTO)
}
