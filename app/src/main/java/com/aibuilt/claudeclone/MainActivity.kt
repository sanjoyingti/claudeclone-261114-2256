package com.aibuilt.claudeclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aibuilt.claudeclone.ui.ClaudeNavHost
import com.aibuilt.claudeclone.ui.theme.ClaudeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ClaudeApp).container
        setContent {
            ClaudeTheme {
                ClaudeNavHost(container)
            }
        }
    }
}
