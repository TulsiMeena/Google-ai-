package com.example.zoya.live

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.example.zoya.personality.ZoyaPersonality

class GeminiLiveSession(
    private val apiKey: String,
    private val modelName: String = "models/gemini-2.5-flash-native-audio-preview-12-2025",
    private val voiceName: String = "Aoede",
    private val customSystemInstruction: String? = null,
    private val onConnected: () -> Unit,
    private val toolsDeclarations: JSONArray? = null,
    private val onToolCallReceived: ((callId: String, name: String, args: JSONObject) -> Unit)? = null,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onTextReceived: (String) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onInterrupted: () -> Unit,
    private val onDiagnosticsLog: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onClosed: () -> Unit
) {
    companion object {
        private const val TAG = "ZoyaLiveSession"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Keep-alive WebSocket connection
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val isConnected = AtomicBoolean(false)

    fun connect() {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            onError("API Key is missing or invalid. Please configure your GEMINI_API_KEY in settings.")
            return
        }

        val formattedModel = if (modelName.startsWith("models/")) modelName else "models/$modelName"
        val wsUrl = "$BASE_WS_URL?key=$apiKey"

        Log.d(TAG, "Connecting to Gemini Live WebSocket with model: $formattedModel")
        onDiagnosticsLog("CONNECTING TO GEMINI LIVE: $formattedModel")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully")
                isConnected.set(true)
                onDiagnosticsLog("GEMINI LIVE WEBSOCKET CONNECTED")

                // Send Setup Frame
                sendSetupFrame(ws, formattedModel)
                onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected.set(false)
                val safeErrorMsg = response?.let {
                    "Connection error: HTTP ${it.code} (${it.message})"
                } ?: "Network error: ${t.localizedMessage ?: "WebSocket disconnected"}"

                onError(safeErrorMsg)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed code=$code reason=$reason")
                isConnected.set(false)
                onDiagnosticsLog("GEMINI LIVE SESSION CLOSED")
                onClosed()
            }
        })
    }

    private fun sendSetupFrame(ws: WebSocket, model: String) {
        try {
            val setupObj = JSONObject().apply {
                put("model", model)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", customSystemInstruction ?: ZoyaPersonality.SYSTEM_INSTRUCTION)
                        })
                    })
                })

                if (toolsDeclarations != null && toolsDeclarations.length() > 0) {
                    put("tools", toolsDeclarations)
                }
            }

            val frame = JSONObject().apply {
                put("setup", setupObj)
            }

            val frameStr = frame.toString()
            Log.d(TAG, "Sending setup frame")
            ws.send(frameStr)
            onDiagnosticsLog("SETUP FRAME SENT: responseModalities=[AUDIO], voice=$voiceName")
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup frame", e)
            onError("Setup frame error: ${e.message}")
        }
    }

    fun sendAudioChunk(pcmAudioBytes: ByteArray) {
        if (!isConnected.get() || webSocket == null || pcmAudioBytes.isEmpty()) return

        try {
            val base64Data = Base64.encodeToString(pcmAudioBytes, Base64.NO_WRAP)

            val realtimeInput = JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Data)
                    })
                })
            }

            val frame = JSONObject().apply {
                put("realtimeInput", realtimeInput)
            }

            webSocket?.send(frame.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    fun sendTextPrompt(promptText: String) {
        if (!isConnected.get() || webSocket == null || promptText.isBlank()) return

        try {
            val partObj = JSONObject().apply {
                put("text", promptText)
            }
            val turnObj = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply { put(partObj) })
            }
            val clientContent = JSONObject().apply {
                put("turns", JSONArray().apply { put(turnObj) })
                put("turnComplete", true)
            }
            val frame = JSONObject().apply {
                put("clientContent", clientContent)
            }

            Log.d(TAG, "Sending text prompt frame: $promptText")
            onDiagnosticsLog("TEXT PROMPT SENT: $promptText")
            webSocket?.send(frame.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text prompt", e)
        }
    }

    fun sendToolResponse(callId: String, functionName: String, responseResult: JSONObject) {
        if (!isConnected.get() || webSocket == null) return

        try {
            val functionResponseObj = JSONObject().apply {
                put("id", callId)
                put("name", functionName)
                put("response", JSONObject().apply {
                    put("result", responseResult)
                })
            }

            val toolResponseObj = JSONObject().apply {
                put("functionResponses", JSONArray().apply {
                    put(functionResponseObj)
                })
            }

            val frame = JSONObject().apply {
                put("toolResponse", toolResponseObj)
            }

            val frameStr = frame.toString()
            Log.d(TAG, "Sending tool response frame: $frameStr")
            onDiagnosticsLog("TOOL RESPONSE SENT: $functionName id=$callId")
            webSocket?.send(frameStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response", e)
        }
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            // Handle toolCall if present
            val toolCallObj = root.optJSONObject("toolCall")
                ?: root.optJSONObject("serverContent")?.optJSONObject("toolCall")

            if (toolCallObj != null) {
                val functionCalls = toolCallObj.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val name = call.optString("name")
                        val id = call.optString("id")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        Log.d(TAG, "Tool call received from Gemini: $name (id=$id)")
                        onDiagnosticsLog("TOOL CALL RECEIVED: $name (id=$id)")
                        onToolCallReceived?.invoke(id, name, args)
                    }
                }
            }

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        var audioChunkCount = 0

                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio payload
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val dataBase64 = inlineData.optString("data", "")
                                if (dataBase64.isNotEmpty()) {
                                    val audioBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                    if (audioBytes.isNotEmpty()) {
                                        audioChunkCount++
                                        val logMsg =
                                            "GEMINI AUDIO RECEIVED bytes = ${audioBytes.size} sampleRate = 24000 format = PCM16"
                                        Log.d(TAG, logMsg)
                                        onDiagnosticsLog(logMsg)
                                        onAudioReceived(audioBytes)
                                    }
                                }
                            }

                            // Text transcription (if present alongside audio)
                            if (part.has("text")) {
                                val text = part.getString("text")
                                if (text.isNotEmpty()) {
                                    onTextReceived(text)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Gemini turn complete")
                    onDiagnosticsLog("GEMINI TURN COMPLETE")
                    onTurnComplete()
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Gemini interrupted")
                    onDiagnosticsLog("GEMINI INTERRUPTED")
                    onInterrupted()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming JSON message", e)
        }
    }

    fun close() {
        if (isConnected.compareAndSet(true, false)) {
            Log.d(TAG, "Closing Gemini Live session")
            webSocket?.close(1000, "User closed session")
            webSocket = null
        }
    }
}
