package io.relavr.sender.platform.webrtc

import android.content.Context
import android.media.AudioRecord
import io.relavr.sender.core.session.AudioCaptureFormat
import io.relavr.sender.core.session.PlaybackAudioCaptureSession
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal data class AudioInputPerformanceSnapshot(
    val captureFormat: AudioCaptureFormat?,
    val callbackCountSinceLastSnapshot: Int,
    val capturedBytesSinceLastSnapshot: Long,
    val shortReadCallbacksSinceLastSnapshot: Int,
    val maxCallbackGapMs: Long,
    val started: Boolean,
    val lastError: String?,
) {
    val hasPressure: Boolean =
        shortReadCallbacksSinceLastSnapshot > 0 || (lastError != null)
}

internal class PlaybackCaptureAudioDeviceModule(
    context: Context,
) : AudioDeviceModule {
    private val metricsObserver = AudioInputMetricsObserver()
    private val delegate =
        JavaAudioDeviceModule
            .builder(context.applicationContext)
            .setInputSampleRate(SAMPLE_RATE_HZ)
            .setOutputSampleRate(SAMPLE_RATE_HZ)
            .setUseStereoInput(true)
            .setUseStereoOutput(false)
            .setEnableVolumeLogger(false)
            .setAudioBufferCallback(metricsObserver)
            .setAudioRecordErrorCallback(metricsObserver)
            .setAudioRecordStateCallback(metricsObserver)
            .createAudioDeviceModule()

    private val audioInput: Any =
        requireNotNull(
            delegate.javaClass
                .getField("audioInput")
                .get(delegate),
        )

    private var installedSession: PlaybackAudioCaptureSession? = null

    fun installPlaybackCapture(session: PlaybackAudioCaptureSession) {
        require(session.format.sampleRateHz == SAMPLE_RATE_HZ) {
            "Unsupported playback-capture sample rate: ${session.format.sampleRateHz}."
        }
        require(session.format.channelCount == CHANNEL_COUNT) {
            "Unsupported playback-capture channel count: ${session.format.channelCount}."
        }

        clearPlaybackCapture()
        configureAudioInput(
            audioInput = audioInput,
            audioRecord = session.audioRecord,
            format = session.format,
        )
        installedSession = session
        metricsObserver.reset(session.format)
    }

    fun clearPlaybackCapture() {
        val session = installedSession
        installedSession = null
        if (session != null) {
            clearAudioInput(audioInput)
            runCatching {
                session.close()
            }
        }
        metricsObserver.reset(null)
    }

    fun snapshotAndReset(): AudioInputPerformanceSnapshot = metricsObserver.snapshotAndReset(installedSession?.format)

    override fun getNative(nativeFactory: Long): Long = delegate.getNative(nativeFactory)

    override fun release() {
        delegate.release()
        clearPlaybackCapture()
    }

    override fun setSpeakerMute(muted: Boolean) {
        delegate.setSpeakerMute(muted)
    }

    override fun setMicrophoneMute(muted: Boolean) {
        delegate.setMicrophoneMute(muted)
    }

    override fun setNoiseSuppressorEnabled(enabled: Boolean): Boolean = delegate.setNoiseSuppressorEnabled(enabled)

    private companion object {
        const val SAMPLE_RATE_HZ = 48_000
        const val CHANNEL_COUNT = 2
    }
}

private class AudioInputMetricsObserver :
    JavaAudioDeviceModule.AudioBufferCallback,
    JavaAudioDeviceModule.AudioRecordErrorCallback,
    JavaAudioDeviceModule.AudioRecordStateCallback {
    private val lock = Any()
    private var callbackCountSinceLastSnapshot: Int = 0
    private var capturedBytesSinceLastSnapshot: Long = 0
    private var shortReadCallbacksSinceLastSnapshot: Int = 0
    private var maxCallbackGapMs: Long = 0
    private var previousCallbackTimeNs: Long? = null
    private var started: Boolean = false
    private var lastError: String? = null

    override fun onBuffer(
        byteBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        bytesRead: Int,
        captureTimeNs: Long,
    ): Long {
        synchronized(lock) {
            callbackCountSinceLastSnapshot += 1
            capturedBytesSinceLastSnapshot += bytesRead.toLong()
            val previousCallbackTimeNsSnapshot = previousCallbackTimeNs
            if (previousCallbackTimeNsSnapshot != null) {
                val callbackGapMs = ((captureTimeNs - previousCallbackTimeNsSnapshot) / 1_000_000L).coerceAtLeast(0L)
                if (callbackGapMs > maxCallbackGapMs) {
                    maxCallbackGapMs = callbackGapMs
                }
            }
            previousCallbackTimeNs = captureTimeNs
            val expectedBytesPer10Ms =
                if (channelCount == 2) {
                    sampleRate / 100 * CHANNELS_STEREO_FRAME_BYTES
                } else {
                    sampleRate / 100 * CHANNELS_MONO_FRAME_BYTES
                }
            if (bytesRead < expectedBytesPer10Ms) {
                shortReadCallbacksSinceLastSnapshot += 1
            }
        }
        return captureTimeNs
    }

    override fun onWebRtcAudioRecordStart() {
        synchronized(lock) {
            started = true
            lastError = null
        }
    }

    override fun onWebRtcAudioRecordStop() {
        synchronized(lock) {
            started = false
        }
    }

    override fun onWebRtcAudioRecordInitError(errorMessage: String) {
        synchronized(lock) {
            lastError = errorMessage
            started = false
        }
    }

    override fun onWebRtcAudioRecordStartError(
        errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
        errorMessage: String,
    ) {
        synchronized(lock) {
            lastError = "${errorCode.name}: $errorMessage"
            started = false
        }
    }

    override fun onWebRtcAudioRecordError(errorMessage: String) {
        synchronized(lock) {
            lastError = errorMessage
            started = false
        }
    }

    fun snapshotAndReset(captureFormat: AudioCaptureFormat?): AudioInputPerformanceSnapshot =
        synchronized(lock) {
            val snapshot =
                AudioInputPerformanceSnapshot(
                    captureFormat = captureFormat,
                    callbackCountSinceLastSnapshot = callbackCountSinceLastSnapshot,
                    capturedBytesSinceLastSnapshot = capturedBytesSinceLastSnapshot,
                    shortReadCallbacksSinceLastSnapshot = shortReadCallbacksSinceLastSnapshot,
                    maxCallbackGapMs = maxCallbackGapMs,
                    started = started,
                    lastError = lastError,
                )
            callbackCountSinceLastSnapshot = 0
            capturedBytesSinceLastSnapshot = 0
            shortReadCallbacksSinceLastSnapshot = 0
            maxCallbackGapMs = 0
            snapshot
        }

    fun reset(captureFormat: AudioCaptureFormat?) {
        synchronized(lock) {
            callbackCountSinceLastSnapshot = 0
            capturedBytesSinceLastSnapshot = 0
            shortReadCallbacksSinceLastSnapshot = 0
            maxCallbackGapMs = 0
            previousCallbackTimeNs = null
            if (captureFormat == null) {
                started = false
                lastError = null
            }
        }
    }

    private companion object {
        const val CHANNELS_STEREO_FRAME_BYTES = 4
        const val CHANNELS_MONO_FRAME_BYTES = 2
    }
}

private fun configureAudioInput(
    audioInput: Any,
    audioRecord: AudioRecord,
    format: AudioCaptureFormat,
) {
    setDeclaredField(audioInput, "sampleRate", format.sampleRateHz)
    setDeclaredField(audioInput, "channelCount", format.channelCount)
    setDeclaredField(audioInput, "expectedSampleRate", format.sampleRateHz)
    setDeclaredField(audioInput, "expectedChannelCount", format.channelCount)
    setDeclaredField(audioInput, "byteBuffer", ByteBuffer.allocateDirect(format.bytesPer10Ms))
    setDeclaredField(audioInput, "emptyBytes", ByteArray(format.bytesPer10Ms))
    setDeclaredField(audioInput, "audioRecord", audioRecord)
    setDeclaredField(audioInput, "useAudioRecord", true)
    setDeclaredField(audioInput, "audioSourceMatchesRecordingSessionRef", AtomicReference<Boolean?>(null))
}

private fun clearAudioInput(audioInput: Any) {
    setDeclaredField(audioInput, "audioRecord", null)
    setDeclaredField(audioInput, "byteBuffer", null)
    setDeclaredField(audioInput, "emptyBytes", null)
    setDeclaredField(audioInput, "sampleRate", 0)
    setDeclaredField(audioInput, "channelCount", 0)
}

private fun setDeclaredField(
    target: Any,
    fieldName: String,
    value: Any?,
) {
    val field =
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
    field.set(target, value)
}

internal data class ProcessMemorySnapshot(
    val javaHeapUsedBytes: Long,
    val javaHeapMaxBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    val nativeHeapFreeBytes: Long,
    val totalPssKb: Int,
)
