package com.example.zoya.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.zoya.audio.AudioState
import com.example.zoya.live.LiveSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {

    val liveSessionManager = LiveSessionManager(viewModelScope, application.applicationContext)

    val audioState: StateFlow<AudioState> = liveSessionManager.audioState
    val lastTextResponse: StateFlow<String?> = liveSessionManager.lastTextResponse
    val errorMessage: StateFlow<String?> = liveSessionManager.errorMessage
    val diagnosticsLogs: StateFlow<List<String>> = liveSessionManager.diagnosticsLogs

    private val defaultApiKey = try {
        BuildConfig.GEMINI_API_KEY
    } catch (e: Exception) {
        ""
    }

    private val _userApiKey = MutableStateFlow(
        if (defaultApiKey.isNotBlank() && defaultApiKey != "MY_GEMINI_API_KEY") defaultApiKey else ""
    )
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.1-flash-live-preview")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _selectedVoice = MutableStateFlow("Aoede")
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    init {
        liveSessionManager.modelName = _selectedModel.value
        liveSessionManager.voiceName = _selectedVoice.value
    }

    fun updateApiKey(newKey: String) {
        _userApiKey.value = newKey.trim()
    }

    fun updateModel(newModel: String) {
        _selectedModel.value = newModel
        liveSessionManager.modelName = newModel
    }

    fun updateVoice(newVoice: String) {
        _selectedVoice.value = newVoice
        liveSessionManager.voiceName = newVoice
    }

    fun getEffectiveApiKey(): String {
        val userKey = _userApiKey.value.trim()
        if (userKey.isNotBlank() && userKey != "MY_GEMINI_API_KEY") {
            return userKey
        }
        return if (defaultApiKey.isNotBlank() && defaultApiKey != "MY_GEMINI_API_KEY") defaultApiKey else ""
    }

    fun toggleSession() {
        val key = getEffectiveApiKey()
        if (key.isBlank()) {
            liveSessionManager.addDiagnosticsLog("ERROR: NO API KEY CONFIGURED")
            liveSessionManager.setError("Gemini API Key is missing! Please tap ⚙️ Settings to enter your Gemini API Key.")
            return
        }
        liveSessionManager.toggleMicrophone(key)
    }

    fun sendTextCommand(text: String) {
        val key = getEffectiveApiKey()
        if (key.isBlank()) {
            liveSessionManager.addDiagnosticsLog("ERROR: NO API KEY CONFIGURED")
            liveSessionManager.setError("Gemini API Key is missing! Please tap ⚙️ Settings to enter your Gemini API Key.")
            return
        }
        liveSessionManager.sendTextCommand(text, key)
    }

    fun resetConversationContext() {
        val key = getEffectiveApiKey()
        liveSessionManager.resetConversationContext(key)
    }

    fun stopSession() {
        liveSessionManager.stopSession()
    }

    fun clearError() {
        liveSessionManager.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        liveSessionManager.stopSession()
    }
}
