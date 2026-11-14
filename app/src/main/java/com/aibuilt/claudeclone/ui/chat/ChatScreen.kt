package com.aibuilt.claudeclone.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aibuilt.claudeclone.R
import com.aibuilt.claudeclone.data.ChatMessage

private val SUGGESTIONS = listOf(
    "Write a haiku about the sea",
    "Explain recursion simply",
    "Plan a weekend in Lisbon"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(state.snackbar) {
        val text = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearSnackbar()
    }

    LaunchedEffect(state.messages.size, state.isSending) {
        if (state.messages.isNotEmpty()) {
            val target = if (state.isSending) {
                (state.messages.size - 1).coerceAtLeast(0)
            } else {
                state.messages.lastIndex
            }
            listState.animateScrollToItem(target)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Claude",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.newChat() }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "New chat"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .imePadding()
            ) {
                InputBar(
                    value = state.input,
                    isSending = state.isSending,
                    onValueChange = viewModel::onInputChange,
                    onSend = {
                        keyboard?.hide()
                        viewModel.send()
                    },
                    onStop = viewModel::stop
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.isLoaded -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                state.messages.isEmpty() -> {
                    EmptyState(
                        onSuggestion = { prompt ->
                            keyboard?.hide()
                            viewModel.send(prompt)
                        }
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items = state.messages, key = { it.id }) { message ->
                            val isLast = message.id == state.messages.lastOrNull()?.id
                            MessageBubble(
                                message = message,
                                isStreaming = state.isSending && isLast,
                                onRetry = { viewModel.retry(message.id) },
                                onCopy = {
                                    copyToClipboard(context, message)
                                    viewModel.copy(message)
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_claude),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "How can I help you today?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        SUGGESTIONS.forEach { suggestion ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onSuggestion(suggestion) }
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Message Claude…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { if (isSending) onStop() else onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSending) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                    contentDescription = if (isSending) "Stop" else "Send",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun copyToClipboard(context: android.content.Context, message: ChatMessage) {
    runCatching {
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText("Claude message", message.text))
    }
}
