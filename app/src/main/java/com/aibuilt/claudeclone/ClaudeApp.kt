package com.aibuilt.claudeclone

import android.app.Application

class ClaudeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
