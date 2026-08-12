package com.example.zoya.live

import android.content.Context
import android.util.Log
import com.example.zoya.audio.AudioRecordManager
import com.example.zoya.audio.AudioState
import com.example.zoya.audio.AudioTrackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import com.example.zoya.tools.ToolExecutionEngine
import org.json.JSONObject

class LiveSessionManager(
    private val scope: CoroutineScope,
    private val context: Context
) {
    companion object {
        private const val TAG = "LiveSessionManager"
    }

    private val toolExecutionEngine = ToolExecutionEngine(context)

    private val _audioState = MutableStateFlow(AudioState.DISCONNECTED)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private val _lastTextResponse = MutableStateFlow<String?>(null)
    val lastTextResponse: StateFlow<String?> = _lastTextResponse.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _diagnosticsLogs = MutableStateFlow<List<String>>(emptyList())
    val diagnosticsLogs: StateFlow<List<String>> = _diagnosticsLogs.asStateFlow()

    private var geminiSession: GeminiLiveSession? = null
    private var audioRecordManager: AudioRecordManager? = null
    private var audioTrackPlayer: AudioTrackPlayer? = null

    private val receivedAudioChunkCount = AtomicInteger(0)
    private val isConnected = AtomicBoolean(false)

    var modelName: String = "models/gemini-2.0-flash-exp"
    var voiceName: String = "Aoede"

    fun addDiagnosticsLog(msg: String) {
        val current = _diagnosticsLogs.value.toMutableList()
        if (current.size > 500) current.removeAt(0)
        current.add("[${System.currentTimeMillis() % 100000}] $msg")
        _diagnosticsLogs.value = current
    }

    fun startSession(apiKey: String) {
        if (_audioState.value == AudioState.CONNECTING || _audioState.value == AudioState.LISTENING) {
            Log.d(TAG, "Session already active")
            return
        }

        _errorMessage.value = null
        _audioState.value = AudioState.CONNECTING
        addDiagnosticsLog("START SESSION INITIATED")

        // 1. Initialize AudioTrack
        audioTrackPlayer = AudioTrackPlayer(
            onDiagnosticsLog = { addDiagnosticsLog(it) },
            onError = { err ->
                Log.e(TAG, "AudioTrack error: $err")
                _errorMessage.value = err
                _audioState.value = AudioState.ERROR
            },
            onPlaybackStarted = {
                _audioState.value = AudioState.SPEAKING
            },
            onPlaybackFinished = {
                if (_audioState.value == AudioState.SPEAKING) {
                    _audioState.value = AudioState.LISTENING
                }
            }
        ).apply {
            start(scope)
        }

        // 2. Initialize AudioRecord
        var pcmChunkCounter = 0
        audioRecordManager = AudioRecordManager(
            onAudioChunk = { chunk ->
                pcmChunkCounter++
                addDiagnosticsLog("PCM_BYTES_CAPTURED = ${chunk.size}")

                val rms = calculatePcmRms(chunk)
                if (_audioState.value == AudioState.SPEAKING) {
                    if (rms > 10000.0) {
                        addDiagnosticsLog("LOUD USER VOICE DETECTED (RMS=${rms.toInt()}) -> INTERRUPTING")
                        handleInterruption()
                        geminiSession?.sendAudioChunk(chunk)
                    }
                    // Filter out lower amplitude speaker feedback during speaking
                } else {
                    geminiSession?.sendAudioChunk(chunk)
                }
            },
            onError = { err ->
                Log.e(TAG, "AudioRecord error: $err")
                _errorMessage.value = err
                _audioState.value = AudioState.ERROR
            }
        )

        // 3. Initialize Gemini Live WebSocket
        receivedAudioChunkCount.set(0)
        geminiSession = GeminiLiveSession(
            apiKey = apiKey,
            modelName = modelName,
            voiceName = voiceName,
            onConnected = {
                isConnected.set(true)
                scope.launch(Dispatchers.Main) {
                    _audioState.value = AudioState.LISTENING
                    audioRecordManager?.startRecording(scope)
                    addDiagnosticsLog("MICROPHONE PIPELINE STARTED (16kHz PCM16)")
                }
            },
            toolsDeclarations = toolExecutionEngine.getToolDeclarations(),
            onToolCallReceived = { callId, name, args ->
                handleToolCall(callId, name, args)
            },
            onAudioReceived = { audioBytes ->
                receivedAudioChunkCount.incrementAndGet()
                audioTrackPlayer?.playChunk(audioBytes)
            },
            onTextReceived = { text ->
                scope.launch(Dispatchers.Main) {
                    _lastTextResponse.value = text
                }
            },
            onTurnComplete = {
                if (receivedAudioChunkCount.get() == 0) {
                    addDiagnosticsLog("WARNING: TURN COMPLETE WITH 0 AUDIO BYTES RECEIVED")
                }
                receivedAudioChunkCount.set(0)
                audioTrackPlayer?.notifyEndOfTurn()
            },
            onInterrupted = {
                handleInterruption()
            },
            onDiagnosticsLog = { addDiagnosticsLog(it) },
            onError = { err ->
                scope.launch(Dispatchers.Main) {
                    Log.e(TAG, "Gemini session error: $err")
                    _errorMessage.value = err
                    _audioState.value = AudioState.ERROR
                    stopSession()
                }
            },
            onClosed = {
                scope.launch(Dispatchers.Main) {
                    if (_audioState.value != AudioState.ERROR) {
                        _audioState.value = AudioState.DISCONNECTED
                    }
                }
            }
        ).apply {
            connect()
        }
    }

    private fun handleToolCall(callId: String, name: String, args: JSONObject) {
        scope.launch(Dispatchers.IO) {
            _audioState.value = AudioState.THINKING
            addDiagnosticsLog("EXECUTING TOOL: $name")
            val toolResult = toolExecutionEngine.executeTool(name, args)
            addDiagnosticsLog("TOOL RESULT: ${toolResult.optBoolean("success")} - ${toolResult.optString("message")}")

            // Return tool result to Gemini Live session
            geminiSession?.sendToolResponse(callId, name, toolResult)
        }
    }

    private fun calculatePcmRms(pcmBytes: ByteArray): Double {
        if (pcmBytes.size < 2) return 0.0
        var sum = 0.0
        val sampleCount = pcmBytes.size / 2
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sum += sample.toDouble() * sample.toDouble()
        }
        return Math.sqrt(sum / sampleCount)
    }

    fun sendTextCommand(text: String, apiKey: String) {
        if (text.isBlank()) return
        addDiagnosticsLog("SEND TEXT COMMAND: $text")
        if (_audioState.value == AudioState.DISCONNECTED || _audioState.value == AudioState.ERROR) {
            startSession(apiKey)
        }
        scope.launch(Dispatchers.IO) {
            // Wait brief moment for socket connection if connecting
            var attempts = 0
            while (!isConnected.get() && attempts < 15) {
                kotlinx.coroutines.delay(200)
                attempts++
            }
            if (isConnected.get()) {
                _audioState.value = AudioState.THINKING
                geminiSession?.sendTextPrompt(text)
            } else {
                addDiagnosticsLog("ERROR: COULD NOT CONNECT TO SEND TEXT COMMAND")
            }
        }
    }

    fun handleInterruption() {
        addDiagnosticsLog("INTERRUPTION TRIGGERED -> STOPPING PLAYBACK")
        audioTrackPlayer?.stopAndFlush()
        scope.launch(Dispatchers.Main) {
            _audioState.value = AudioState.LISTENING
        }
    }

    fun toggleMicrophone(apiKey: String) {
        when (_audioState.value) {
            AudioState.DISCONNECTED, AudioState.ERROR -> {
                startSession(apiKey)
            }
            AudioState.SPEAKING -> {
                handleInterruption()
            }
            AudioState.LISTENING, AudioState.THINKING, AudioState.CONNECTING -> {
                stopSession()
            }
        }
    }

    fun stopSession() {
        addDiagnosticsLog("STOPPING SESSION & RELEASING RESOURCES")
        isConnected.set(false)

        audioRecordManager?.release()
        audioRecordManager = null

        audioTrackPlayer?.release()
        audioTrackPlayer = null

        geminiSession?.close()
        geminiSession = null

        _audioState.value = AudioState.DISCONNECTED
    }

    fun resetConversationContext(apiKey: String) {
        addDiagnosticsLog("RESET CONVERSATION CONTEXT REQUESTED")
        _lastTextResponse.value = null
        if (_audioState.value != AudioState.DISCONNECTED) {
            stopSession()
            startSession(apiKey)
        }
    }

    fun setError(msg: String) {
        _errorMessage.value = msg
        _audioState.value = AudioState.ERROR
    }

    fun clearError() {
        _errorMessage.value = null
        if (_audioState.value == AudioState.ERROR) {
            _audioState.value = AudioState.DISCONNECTED
        }
    }
}
