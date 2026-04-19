package io.relavr.sender.platform.androidcapture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import io.relavr.sender.core.session.AudioCaptureFormat
import io.relavr.sender.core.session.PlaybackAudioCaptureSession
import io.relavr.sender.core.session.PlaybackAudioCaptureSessionFactory

class AndroidPlaybackAudioCaptureSessionFactory : PlaybackAudioCaptureSessionFactory {
    @SuppressLint("MissingPermission")
    override fun create(mediaProjection: MediaProjection): PlaybackAudioCaptureSession {
        val captureConfig =
            AudioPlaybackCaptureConfiguration
                .Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

        playbackCaptureCandidates().forEach { candidate ->
            val audioRecord =
                buildAudioRecord(
                    captureConfig = captureConfig,
                    format = candidate,
                )
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                return AndroidPlaybackAudioCaptureSession(audioRecord = audioRecord, format = candidate)
            }
            audioRecord.release()
        }

        throw IllegalStateException("Unable to initialize AudioPlaybackCapture for any supported PCM format.")
    }

    @SuppressLint("MissingPermission")
    private fun buildAudioRecord(
        captureConfig: AudioPlaybackCaptureConfiguration,
        format: AudioCaptureFormat,
    ): AudioRecord {
        val channelMask =
            if (format.channelCount == 2) {
                AudioFormat.CHANNEL_IN_STEREO
            } else {
                AudioFormat.CHANNEL_IN_MONO
            }
        val audioFormat =
            AudioFormat
                .Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(format.sampleRateHz)
                .setChannelMask(channelMask)
                .build()
        val minBufferSize =
            AudioRecord.getMinBufferSize(
                format.sampleRateHz,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        val bufferSize = maxOf(minBufferSize * 2, format.bytesPer10Ms * 4)
        return AudioRecord
            .Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
    }

    private fun playbackCaptureCandidates(): List<AudioCaptureFormat> =
        listOf(
            AudioCaptureFormat(
                sampleRateHz = SAMPLE_RATE_HZ,
                channelCount = 2,
            ),
            AudioCaptureFormat(
                sampleRateHz = SAMPLE_RATE_HZ,
                channelCount = 1,
            ),
        )

    private companion object {
        const val SAMPLE_RATE_HZ = 48_000
    }
}

private class AndroidPlaybackAudioCaptureSession(
    override val audioRecord: AudioRecord,
    override val format: AudioCaptureFormat,
) : PlaybackAudioCaptureSession {
    override fun close() {
        runCatching {
            audioRecord.stop()
        }
        audioRecord.release()
    }
}
