package com.example.zoya.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecordManager(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "ZoyaAudioRecord"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Standard chunk size: ~100ms of 16kHz 16-bit mono PCM = 3200 bytes
        private const val CHUNK_SIZE = 3200
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope) {
        if (isRecording.get()) {
            Log.d(TAG, "Already recording")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize <= 0) {
            onError("Invalid AudioRecord buffer size")
            return
        }

        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_RECOGNITION fails
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            Log.d(TAG, "AudioRecord started: rate=$SAMPLE_RATE, bufferSize=$bufferSize")

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE)
                while (isActive && isRecording.get()) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        onAudioChunk(chunk)
                    } else if (readBytes < 0) {
                        Log.e(TAG, "Error reading AudioRecord: $readBytes")
                        if (readBytes == AudioRecord.ERROR_INVALID_OPERATION) {
                            onError("AudioRecord read error")
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            isRecording.set(false)
            onError("Microphone error: ${e.message}")
        }
    }

    fun stopRecording() {
        if (!isRecording.compareAndSet(true, false)) {
            return
        }

        Log.d(TAG, "Stopping AudioRecord")
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    fun release() {
        stopRecording()
    }
}
