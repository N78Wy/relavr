package io.relavr.sender.platform.webrtc

import android.util.Log
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.session.AudioCaptureSource
import io.relavr.sender.core.session.RtcPublishSession
import io.relavr.sender.core.session.RtcPublisherFactory
import io.relavr.sender.core.session.VideoCaptureSource

class LoggingRtcPublisherFactory : RtcPublisherFactory {
    override suspend fun createSession(config: StreamConfig): RtcPublishSession = LoggingRtcPublishSession(config)
}

class LoggingRtcPublishSession(
    private val config: StreamConfig,
) : RtcPublishSession {
    override suspend fun publish(
        videoSource: VideoCaptureSource,
        audioSource: AudioCaptureSource?,
    ) {
        Log.i(
            TAG,
            "启动占位推流会话，codec=${config.codecPreference.displayName}, video=${videoSource.description}, audio=${audioSource?.enabled ?: false}",
        )
    }

    override fun close() {
        Log.i(TAG, "关闭占位推流会话")
    }

    private companion object {
        const val TAG = "LoggingRtcPublisher"
    }
}
