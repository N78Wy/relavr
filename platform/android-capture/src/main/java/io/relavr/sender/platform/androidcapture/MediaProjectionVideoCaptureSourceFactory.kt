package io.relavr.sender.platform.androidcapture

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.SenderException
import io.relavr.sender.core.session.VideoCaptureSource
import io.relavr.sender.core.session.VideoCaptureSourceFactory

class MediaProjectionVideoCaptureSourceFactory(
    private val mediaProjectionManager: MediaProjectionManager,
) : VideoCaptureSourceFactory {
    override suspend fun create(
        projectionAccess: ProjectionAccess,
        config: StreamConfig,
    ): VideoCaptureSource {
        val access =
            projectionAccess as? AndroidProjectionAccess
                ?: throw SenderException(
                    io.relavr.sender.core.model.SenderError
                        .SessionStartFailed("无效的屏幕采集授权结果"),
                )

        val mediaProjection =
            mediaProjectionManager.getMediaProjection(
                access.resultCode,
                Intent(access.resultData),
            ) ?: throw SenderException(
                io.relavr.sender.core.model.SenderError
                    .SessionStartFailed("无法创建 MediaProjection 会话"),
            )

        return MediaProjectionVideoCaptureSource(
            mediaProjection = mediaProjection,
            description = "MediaProjection ${config.resolution.label} @ ${config.fps}fps",
        )
    }
}

class MediaProjectionVideoCaptureSource(
    private val mediaProjection: MediaProjection,
    override val description: String,
) : VideoCaptureSource {
    override fun close() {
        mediaProjection.stop()
    }
}
