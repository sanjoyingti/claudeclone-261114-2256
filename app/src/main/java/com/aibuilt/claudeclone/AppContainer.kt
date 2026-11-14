package com.aibuilt.claudeclone

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibuilt.claudeclone.data.ApiConfigInterceptor
import com.aibuilt.claudeclone.data.ChatApi
import com.aibuilt.claudeclone.data.ChatRepository
import com.aibuilt.claudeclone.data.SettingsRepository
import com.aibuilt.claudeclone.ui.chat.ChatViewModel
import com.aibuilt.claudeclone.ui.settings.SettingsViewModel
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settingsRepository = SettingsRepository(appContext, scope)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .addInterceptor(ApiConfigInterceptor(settingsRepository))
        .build()

    private val chatApi = ChatApi(httpClient, json)

    private val chatRepository = ChatRepository(
        file = File(appContext.filesDir, "claude_conversation.json"),
        json = json
    )

    val chatViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { ChatViewModel(chatRepository, chatApi) }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(settingsRepository, chatApi) }
    }
}
