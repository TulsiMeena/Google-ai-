package com.example.zoya.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AudioTrackPlayer(
    private val onDiagnosticsLog: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPlaybackStarted: () -> Unit = {},
    private val onPlaybackFinished: () -> Unit = {}
) {
    companion object {
        private const val TAG = "ZoyaAudioTrack"
        const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    private val isPlaying = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private val endOfTurnSignaled = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (isInitialized.get()) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize <= 0) {
            val errorMsg = "AUDIO PLAYBACK ERROR: Invalid AudioTrack buffer size ($minBufferSize)"
            Log.e(TAG, errorMsg)
            onError(errorMsg)
            return
        }

        val bufferSize = maxOf(minBufferSize, 24000)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                val errorMsg = "AUDIO PLAYBACK ERROR: AudioTrack failed to initialize"
                Log.e(TAG, errorMsg)
                onError(errorMsg)
                return
            }

            audioTrack?.play()
            isInitialized.set(true)
            val startLog = "AUDIOTRACK_STARTED sampleRate = $SAMPLE_RATE"
            Log.d(TAG, startLog)
            onDiagnosticsLog(startLog)

            audioChannel = Channel(Channel.UNLIMITED)
            endOfTurnSignaled.set(false)

            playbackJob = scope.launch(Dispatchers.IO) {
                while (isActive && isInitialized.get()) {
                    val result = audioChannel.receiveCatching()
                    if (result.isClosed) break

                    val chunk = result.getOrNull()
                    if (chunk != null) {
                        try {
                            if (!isPlaying.get()) {
                                isPlaying.set(true)
                                onPlaybackStarted()
                            }

                            val written = audioTrack?.write(chunk, 0, chunk.size) ?: -1
                            if (written > 0) {
                                val writeLog = "AUDIOTRACK_AUDIO_WRITTEN = $written"
                                Log.d(TAG, writeLog)
                                onDiagnosticsLog(writeLog)
                            } else if (written < 0) {
                                val err = "AUDIO PLAYBACK ERROR: AudioTrack write failed code = $written"
                                Log.e(TAG, err)
                                onError(err)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during AudioTrack write", e)
                            onError("AUDIO PLAYBACK ERROR: ${e.message}")
                        }
                    }

                    // Check if channel is empty and end of turn was requested
                    if (audioChannel.isEmpty && endOfTurnSignaled.get()) {
                        kotlinx.coroutines.delay(200) // allow last audio buffer to play
                        if (audioChannel.isEmpty) {
                            endOfTurnSignaled.set(false)
                            if (isPlaying.compareAndSet(true, false)) {
                                onPlaybackFinished()
                            }
                        }
                    }
                }

                if (isPlaying.compareAndSet(true, false)) {
                    onPlaybackFinished()
                }
            }
        } catch (e: Exception) {
            val err = "AUDIO PLAYBACK ERROR: ${e.message}"
            Log.e(TAG, "Failed to create AudioTrack", e)
            onError(err)
        }
    }

    fun playChunk(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        val sent = audioChannel.trySend(chunk)
        if (sent.isFailure) {
            Log.w(TAG, "Failed to enqueue audio chunk")
        }
    }

    fun notifyEndOfTurn() {
        endOfTurnSignaled.set(true)
        if (audioChannel.isEmpty && isPlaying.get()) {
            // Drain trigger
            audioChannel.trySend(ByteArray(0))
        }
    }

    fun stopAndFlush() {
        Log.d(TAG, "stopAndFlush requested")
        endOfTurnSignaled.set(false)
        // Clear channel queue
        while (audioChannel.tryReceive().isSuccess) {
            // drain queue
        }

        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                    play()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        }

        if (isPlaying.compareAndSet(true, false)) {
            onPlaybackFinished()
        }
    }

    fun release() {
        stopAndFlush()
        isInitialized.set(false)
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }
}
