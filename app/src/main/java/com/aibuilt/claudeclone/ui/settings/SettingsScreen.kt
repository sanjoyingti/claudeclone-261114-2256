package com.aibuilt.claudeclone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aibuilt.claudeclone.data.ApiProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        val text = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearSnackbar()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Settings", style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Point the app at your own API — Anthropic or any OpenAI-compatible server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProtocolChip(
                    label = "Auto-detect",
                    selected = state.protocol == ApiProtocol.AUTO,
                    onClick = { viewModel.onProtocolChange(ApiProtocol.AUTO) }
                )
                ProtocolChip(
                    label = "Anthropic",
                    selected = state.protocol == ApiProtocol.ANTHROPIC,
                    onClick = { viewModel.onProtocolChange(ApiProtocol.ANTHROPIC) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ProtocolChip(
                    label = "OpenAI-compatible",
                    selected = state.protocol == ApiProtocol.OPENAI,
                    onClick = { viewModel.onProtocolChange(ApiProtocol.OPENAI) }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.protocol == ApiProtocol.AUTO) {
                    "From this address the app would use the " +
                        if (state.detectedProtocol == ApiProtocol.ANTHROPIC) {
                            "Anthropic format."
                        } else {
                            "OpenAI-compatible format."
                        }
                } else {
                    "Using the ${if (state.activeProtocol == ApiProtocol.ANTHROPIC) "Anthropic" else "OpenAI-compatible"} format."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("Base URL") },
                singleLine = true,
                isError = state.baseUrlError != null,
                supportingText = {
                    Text(state.baseUrlError ?: "Example: https://api.example.com/v1")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (state.isKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleKeyVisibility) {
                        Icon(
                            imageVector = if (state.isKeyVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (state.isKeyVisible) {
                                "Hide API key"
                            } else {
                                "Show API key"
                            }
                        )
                    }
                },
                supportingText = { Text("Sent in the header your chosen format expects.") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = { Text("Model") },
                singleLine = true,
                isError = state.modelError != null,
                supportingText = {
                    Text(
                        state.modelError
                            ?: "The model name your server expects, for example gpt-4o-mini."
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !state.isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Testing…")
                } else {
                    Text("Test connection")
                }
            }

            val testMessage = state.testMessage
            if (testMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                val succeeded = state.testSucceeded == true
                Surface(
                    color = if (succeeded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (succeeded) "✓" else "!",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (succeeded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = testMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (succeeded) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = viewModel::reset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to original defaults")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Leave a field empty to keep using the built-in endpoint and key.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.Bottom) { }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(12.dp)
    )
}
