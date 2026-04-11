package io.relavr.sender.platform.androidcapture

import android.os.Build
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.session.AudioCaptureSource
import io.relavr.sender.core.session.AudioCaptureSourceFactory
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.SenderException

class PlaybackAudioCaptureSourceFactory : AudioCaptureSourceFactory {
    override suspend fun create(
        projectionAccess: ProjectionAccess,
        config: StreamConfig,
    ): AudioCaptureSource? {
        if (!config.audioEnabled) {
            return null
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw SenderException(
                SenderError.AudioCaptureUnavailable("当前系统版本不支持 AudioPlaybackCapture"),
            )
        }

        return PlaybackAudioCaptureSource(enabled = true)
    }
}

data class PlaybackAudioCaptureSource(
    override val enabled: Boolean,
) : AudioCaptureSource {
    override fun close() = Unit
}
