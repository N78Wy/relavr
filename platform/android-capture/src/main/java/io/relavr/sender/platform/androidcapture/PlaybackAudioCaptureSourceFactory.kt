package io.relavr.sender.platform.androidcapture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.projection.MediaProjection
import android.os.Build
import androidx.core.content.ContextCompat
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.session.AudioCaptureSource
import io.relavr.sender.core.session.AudioCaptureSourceFactory
import io.relavr.sender.core.session.AudioFrameReadResult
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.SenderException
import java.nio.ByteBuffer
import kotlin.math.max

class PlaybackAudioCaptureSourceFactory(
    private val appContext: Context,
) : AudioCaptureSourceFactory {
    override suspend fun create(
        projectionAccess: ProjectionAccess,
        config: StreamConfig,
    ): AudioCaptureSource? {
        if (!config.audioEnabled) {
            return null
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw SenderException(
                SenderError.AudioCaptureUnavailable("AudioPlaybackCapture is not supported on this Android version."),
            )
        }

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SenderException(
                SenderError.AudioCaptureUnavailable("The audio-record permission is missing, so audio streaming is unavailable."),
            )
        }

        return PlaybackAudioCaptureSource()
    }
}

@SuppressLint("MissingPermission")
class PlaybackAudioCaptureSource : AudioCaptureSource {
    override val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ
    override val channelCount: Int = DEFAULT_CHANNEL_COUNT

    private val audioTimestamp = AudioTimestamp()
    private var audioRecord: AudioRecord? = null

    override fun start(mediaProjection: MediaProjection) {
        if (audioRecord != null) {
            return
        }

        val audioFormat =
            AudioFormat
                .Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
        val minBufferSize =
            AudioRecord.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBufferSize <= 0) {
            throw SenderException(
                SenderError.AudioCaptureUnavailable("Unable to initialize the audio capture buffer."),
            )
        }

        val captureConfig =
            AudioPlaybackCaptureConfiguration
                .Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
        val record =
            AudioRecord
                .Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(max(minBufferSize, MIN_CAPTURE_BUFFER_BYTES))
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw SenderException(
                SenderError.AudioCaptureUnavailable("The system rejected AudioPlaybackCapture initialization."),
            )
        }

        try {
            record.startRecording()
        } catch (throwable: Throwable) {
            record.release()
            throw SenderException(
                SenderError.AudioCaptureUnavailable(
                    throwable.message ?: "Starting AudioPlaybackCapture failed.",
                ),
            )
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            throw SenderException(
                SenderError.AudioCaptureUnavailable("System playback capture did not enter the recording state."),
            )
        }

        audioRecord = record
    }

    override fun read(
        targetBuffer: ByteBuffer,
        requestedBytes: Int,
    ): AudioFrameReadResult {
        val record =
            audioRecord ?: throw SenderException(
                SenderError.AudioCaptureUnavailable("System playback capture has not been started yet."),
            )
        if (requestedBytes <= 0) {
            return AudioFrameReadResult(
                bytesRead = 0,
                timestampNs = System.nanoTime(),
            )
        }

        var totalBytesRead = 0
        var remainingBytes = requestedBytes
        while (remainingBytes > 0) {
            val bytesRead =
                record.read(
                    targetBuffer,
                    remainingBytes,
                    AudioRecord.READ_BLOCKING,
                )
            when {
                bytesRead > 0 -> {
                    totalBytesRead += bytesRead
                    remainingBytes -= bytesRead
                }

                bytesRead == 0 -> break

                bytesRead == AudioRecord.ERROR_DEAD_OBJECT ->
                    throw SenderException(
                        SenderError.AudioCaptureUnavailable("System playback capture was interrupted."),
                    )

                else ->
                    throw SenderException(
                        SenderError.AudioCaptureUnavailable("Reading system playback audio failed."),
                    )
            }
        }

        val timestampNs =
            if (
                record.getTimestamp(
                    audioTimestamp,
                    AudioTimestamp.TIMEBASE_MONOTONIC,
                ) == AudioRecord.SUCCESS
            ) {
                audioTimestamp.nanoTime
            } else {
                System.nanoTime()
            }
        return AudioFrameReadResult(
            bytesRead = totalBytesRead,
            timestampNs = timestampNs,
        )
    }

    override fun close() {
        val record = audioRecord ?: return
        audioRecord = null
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
        record.release()
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 48_000
        const val DEFAULT_CHANNEL_COUNT = 2
        const val MIN_CAPTURE_BUFFER_BYTES = 19_200
    }
}
