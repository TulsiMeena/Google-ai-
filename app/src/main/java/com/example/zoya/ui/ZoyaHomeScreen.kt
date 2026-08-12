package com.example.zoya.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.zoya.audio.AudioState
import com.example.zoya.viewmodel.ZoyaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoyaHomeScreen(viewModel: ZoyaViewModel) {
    val context = LocalContext.current
    val audioState by viewModel.audioState.collectAsState()
    val lastTextResponse by viewModel.lastTextResponse.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val diagnosticsLogs by viewModel.diagnosticsLogs.collectAsState()
    val apiKey by viewModel.userApiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }

    // Audio record permission launcher
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.toggleSession()
        }
    }

    val effectiveKey = viewModel.getEffectiveApiKey()
    val isKeyConfigured = effectiveKey.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090710)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ZoyaTopBar(
                    audioState = audioState,
                    isKeyConfigured = isKeyConfigured,
                    onOpenLogs = { showLogsSheet = true },
                    onOpenSettings = { showSettingsSheet = true }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Title & State Status
                    HeaderStatusSection(audioState = audioState)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Central Glowing Voice Orb
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ZoyaVoiceOrb(
                            audioState = audioState,
                            onClick = {
                                if (!isKeyConfigured) {
                                    showSettingsSheet = true
                                    viewModel.toggleSession()
                                } else if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleSession()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Subtitles / Caption Card
                    LiveCaptionCard(
                        lastTextResponse = lastTextResponse,
                        audioState = audioState,
                        errorMessage = errorMessage,
                        isKeyConfigured = isKeyConfigured,
                        onClearError = { viewModel.clearError() },
                        onOpenSettings = { showSettingsSheet = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Text Command Bar
                    QuickTextCommandBar(
                        onSendText = { text ->
                            if (!isKeyConfigured) {
                                showSettingsSheet = true
                                viewModel.sendTextCommand(text)
                            } else {
                                viewModel.sendTextCommand(text)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Controls Section
                    ControlSection(
                        audioState = audioState,
                        isKeyConfigured = isKeyConfigured,
                        hasMicPermission = hasMicPermission,
                        onMicClick = {
                            if (!isKeyConfigured) {
                                showSettingsSheet = true
                                viewModel.toggleSession()
                            } else if (!hasMicPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.toggleSession()
                            }
                        },
                        onResetContext = {
                            viewModel.resetConversationContext()
                        },
                        onOpenSettings = { showSettingsSheet = true }
                    )
                }
            }
        }
    }

    // Settings Modal Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF161224)
        ) {
            SettingsSheetContent(
                apiKey = apiKey,
                selectedModel = selectedModel,
                selectedVoice = selectedVoice,
                onApiKeyChange = { viewModel.updateApiKey(it) },
                onModelChange = { viewModel.updateModel(it) },
                onVoiceChange = { viewModel.updateVoice(it) },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }

    // Diagnostics Logs Sheet
    if (showLogsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLogsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF110D1C)
        ) {
            DiagnosticsLogsSheetContent(
                logs = diagnosticsLogs,
                onDismiss = { showLogsSheet = false }
            )
        }
    }
}

@Composable
fun ZoyaTopBar(
    audioState: AudioState,
    isKeyConfigured: Boolean,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (audioState) {
                            AudioState.DISCONNECTED -> Color.Gray
                            AudioState.CONNECTING -> Color(0xFF38BDF8)
                            AudioState.LISTENING -> Color(0xFF22C55E)
                            AudioState.THINKING -> Color(0xFFF59E0B)
                            AudioState.SPEAKING -> Color(0xFFEC4899)
                            AudioState.ERROR -> Color(0xFFEF4444)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ZOYA LIVE",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.2.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isKeyConfigured) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33EF4444))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "API Key Required",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(
                onClick = onOpenLogs,
                modifier = Modifier.testTag("logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Developer Logs",
                    tint = Color(0xFFA78BFA)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun HeaderStatusSection(audioState: AudioState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val (titleText, subtitleText, statusColor) = when (audioState) {
            AudioState.DISCONNECTED -> Triple(
                "Tap to start Zoya",
                "Real-Time Bidirectional Voice Pipeline",
                Color(0xFF9CA3AF)
            )
            AudioState.CONNECTING -> Triple(
                "Connecting to Gemini Live...",
                "Establishing PCM Audio Stream",
                Color(0xFF38BDF8)
            )
            AudioState.LISTENING -> Triple(
                "Listening...",
                "Say \"Zoya, hello\" or ask a question",
                Color(0xFF4ADE80)
            )
            AudioState.THINKING -> Triple(
                "Zoya is thinking...",
                "Processing audio turn",
                Color(0xFFFBBF24)
            )
            AudioState.SPEAKING -> Triple(
                "Zoya Speaking",
                "24kHz PCM Native Gemini Audio",
                Color(0xFFF472B6)
            )
            AudioState.ERROR -> Triple(
                "Connection Error",
                "Tap microphone to retry connection",
                Color(0xFFF87171)
            )
        }

        Text(
            text = titleText,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitleText,
            color = statusColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ZoyaVoiceOrb(
    audioState: AudioState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (audioState == AudioState.LISTENING || audioState == AudioState.SPEAKING) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    val primaryColor = when (audioState) {
        AudioState.DISCONNECTED -> Color(0xFF6B7280)
        AudioState.CONNECTING -> Color(0xFF0284C7)
        AudioState.LISTENING -> Color(0xFF10B981)
        AudioState.THINKING -> Color(0xFFD97706)
        AudioState.SPEAKING -> Color(0xFFDB2777)
        AudioState.ERROR -> Color(0xFFDC2626)
    }

    val secondaryColor = when (audioState) {
        AudioState.DISCONNECTED -> Color(0xFF374151)
        AudioState.CONNECTING -> Color(0xFF38BDF8)
        AudioState.LISTENING -> Color(0xFF6EE7B7)
        AudioState.THINKING -> Color(0xFFFDE047)
        AudioState.SPEAKING -> Color(0xFFF472B6)
        AudioState.ERROR -> Color(0xFFFCA5A5)
    }

    Box(
        modifier = Modifier
            .size(240.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing halo ring
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(pulseScale)
                .rotate(rotationAngle)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            primaryColor.copy(alpha = 0.4f),
                            secondaryColor.copy(alpha = 0.7f),
                            primaryColor.copy(alpha = 0.2f),
                            secondaryColor.copy(alpha = 0.8f),
                            primaryColor.copy(alpha = 0.4f)
                        )
                    )
                )
                .blur(20.dp)
        )

        // Inner core
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            secondaryColor,
                            primaryColor,
                            Color(0xFF180E29)
                        )
                    )
                )
                .border(2.dp, secondaryColor.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (audioState) {
                    AudioState.SPEAKING -> Icons.Default.VolumeUp
                    AudioState.LISTENING -> Icons.Default.GraphicEq
                    AudioState.THINKING -> Icons.Default.GraphicEq
                    AudioState.ERROR -> Icons.Default.Warning
                    else -> Icons.Default.Mic
                },
                contentDescription = "Orb State",
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }
    }
}

@Composable
fun LiveCaptionCard(
    lastTextResponse: String?,
    audioState: AudioState,
    errorMessage: String?,
    isKeyConfigured: Boolean,
    onClearError: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onOpenSettings() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFF87171))))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Configuration / Audio Error",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = errorMessage,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClearError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Error",
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (!isKeyConfigured) onOpenSettings()
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!isKeyConfigured) Color(0x33F59E0B) else Color(0xFF140E24)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = if (!isKeyConfigured) {
                        Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0x33A78BFA), Color(0x3338BDF8)))
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (!isKeyConfigured) "⚠️ SETUP REQUIRED" else "LIVE TRANSCRIPTION",
                        color = if (!isKeyConfigured) Color(0xFFFCD34D) else Color(0xFFA78BFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            !isKeyConfigured -> "Gemini API Key missing! Tap here or ⚙️ Settings to enter your key."
                            !lastTextResponse.isNull_or_blank() -> "\"$lastTextResponse\""
                            audioState == AudioState.SPEAKING -> "Streaming native Gemini PCM audio..."
                            audioState == AudioState.LISTENING -> "Listening to microphone..."
                            audioState == AudioState.CONNECTING -> "Connecting to Live session..."
                            else -> "Tap the Voice Orb or Mic button below to start Zoya AI."
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()

@Composable
fun ControlSection(
    audioState: AudioState,
    isKeyConfigured: Boolean,
    hasMicPermission: Boolean,
    onMicClick: () -> Unit,
    onResetContext: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isActive = audioState != AudioState.DISCONNECTED && audioState != AudioState.ERROR

        // Reset Context / New Chat Button
        IconButton(
            onClick = onResetContext,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x22A78BFA))
                .border(1.dp, Color(0x66A78BFA), CircleShape)
                .testTag("reset_context_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset Conversation Context",
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Main Mic Toggle Button
        Button(
            onClick = onMicClick,
            modifier = Modifier
                .size(72.dp)
                .testTag("mic_toggle_button"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) Color(0xFFEF4444) else Color(0xFF8B5CF6)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isActive) "Stop Zoya" else "Start Zoya",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Settings Shortcut Button
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .testTag("quick_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheetContent(
    apiKey: String,
    selectedModel: String,
    selectedVoice: String,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(apiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Zoya Voice Settings",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // API Key Field
        OutlinedTextField(
            value = keyInput,
            onValueChange = {
                keyInput = it
                onApiKeyChange(it)
            },
            label = { Text("Gemini API Key") },
            placeholder = { Text("Paste API Key") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFA78BFA)) },
            trailingIcon = {
                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                    Icon(
                        imageVector = if (isKeyVisible) Icons.Default.Check else Icons.Default.Key,
                        contentDescription = "Toggle Visibility",
                        tint = Color.Gray
                    )
                }
            },
            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api_key_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFA78BFA),
                unfocusedBorderColor = Color(0x66A78BFA),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Model Selector Dropdown
        Text("Gemini Live Model", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))

        val models = listOf(
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-12-2025"
        )
        var modelExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = !modelExpanded }
        ) {
            OutlinedTextField(
                value = selectedModel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFA78BFA),
                    unfocusedBorderColor = Color(0x66A78BFA),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                models.forEach { modelOption ->
                    DropdownMenuItem(
                        text = { Text(modelOption) },
                        onClick = {
                            onModelChange(modelOption)
                            modelExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voice Selector Dropdown
        Text("Voice Preset", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))

        val voices = listOf("Aoede", "Kore", "Puck", "Charon", "Fenrir")
        var voiceExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = selectedVoice,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFA78BFA),
                    unfocusedBorderColor = Color(0x66A78BFA),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voiceOption ->
                    DropdownMenuItem(
                        text = { Text(voiceOption) },
                        onClick = {
                            onVoiceChange(voiceOption)
                            voiceExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
        ) {
            Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DiagnosticsLogsSheetContent(
    logs: List<String>,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Developer Diagnostics Logs",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF07050C)),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No diagnostics logs recorded yet.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = when {
                                log.contains("GEMINI AUDIO RECEIVED") -> Color(0xFF4ADE80)
                                log.contains("AUDIOTRACK") -> Color(0xFFF472B6)
                                log.contains("ERROR") || log.contains("WARNING") -> Color(0xFFF87171)
                                else -> Color(0xFF38BDF8)
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickTextCommandBar(
    onSendText: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = textInput,
            onValueChange = { textInput = it },
            modifier = Modifier
                .weight(1f)
                .testTag("text_command_input"),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (textInput.isEmpty()) {
                    Text(
                        text = "Type command (e.g. Camera kholo)...",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                }
                innerTextField()
            }
        )

        IconButton(
            onClick = {
                if (textInput.isNotBlank()) {
                    onSendText(textInput)
                    textInput = ""
                }
            },
            modifier = Modifier
                .size(36.dp)
                .testTag("send_command_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send Command",
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
