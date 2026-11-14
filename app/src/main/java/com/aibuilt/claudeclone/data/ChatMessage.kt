package com.aibuilt.claudeclone.data

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false
)
