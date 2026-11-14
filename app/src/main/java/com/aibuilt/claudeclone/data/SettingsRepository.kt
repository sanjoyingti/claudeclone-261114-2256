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
            val storedUrl = runCatching {
                dataStore.data.map { it[BASE_URL_KEY] ?: "" }.first()
            }.getOrDefault("")
            val storedKey = withContext(Dispatchers.IO) {
                runCatching { prefs.getString(API_KEY_PREF, "").orEmpty() }.getOrDefault("")
            }
            _state.value = ApiSettings(storedUrl, storedKey)
        }
    }

    suspend fun save(rawBaseUrl: String, rawApiKey: String) {
        val baseUrl = normalizeBaseUrl(rawBaseUrl)
        val apiKey = rawApiKey.trim()
        withContext(Dispatchers.IO) {
            runCatching { dataStore.edit { it[BASE_URL_KEY] = baseUrl } }
            runCatching {
                prefs.edit().putString(API_KEY_PREF, apiKey).apply()
            }
        }
        _state.value = ApiSettings(baseUrl, apiKey)
    }

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            runCatching { dataStore.edit { it.remove(BASE_URL_KEY) } }
            runCatching { prefs.edit().remove(API_KEY_PREF).apply() }
        }
        _state.value = ApiSettings()
    }

    /** The address actually used for a request, with the original as fallback. */
    fun requestBaseUrl(): String {
        val raw = override?.baseUrl ?: _state.value.baseUrl
        val normalized = normalizeBaseUrl(raw)
        return normalized.ifBlank { ApiDefaults.BASE_URL }
    }

    /** The key actually sent with a request, with the original as fallback. */
    fun requestApiKey(): String {
        val raw = override?.apiKey ?: _state.value.apiKey
        return raw.trim().ifBlank { ApiDefaults.API_KEY }
    }
}
