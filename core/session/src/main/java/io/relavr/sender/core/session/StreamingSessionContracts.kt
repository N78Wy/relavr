package io.relavr.sender.core.session

import android.content.Intent
import android.media.projection.MediaProjection
import io.relavr.sender.core.model.AudioState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoStreamProfile
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

data class MediaProjectionPermission(
    val resultCode: Int,
    val resultData: Intent,
)

interface ProjectionAccess : Closeable {
    fun mediaProjectionPermission(): MediaProjectionPermission? = null
}

interface ProjectionPermissionGateway {
    suspend fun requestPermission(): ProjectionAccess

    fun restoreIfAvailable(): ProjectionAccess?
}

interface CodecCapabilityRepository {
    suspend fun getCapabilities(): CapabilitySnapshot
}

data class AudioCaptureFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bytesPerSample: Int = 2,
) {
    val bytesPerFrame: Int = channelCount * bytesPerSample
    val bytesPer10Ms: Int = (sampleRateHz / 100) * bytesPerFrame
}

interface PlaybackAudioCaptureSession : Closeable {
    val format: AudioCaptureFormat

    fun start()

    fun read(
        buffer: ByteArray,
        offsetInBytes: Int,
        sizeInBytes: Int,
    ): Int
}

fun interface PlaybackAudioCaptureSessionFactory {
    fun create(mediaProjection: MediaProjection): PlaybackAudioCaptureSession
}

fun interface CodecPolicy {
    fun select(
        preference: CodecPreference,
        capabilities: CapabilitySnapshot,
    ): CodecSelection
}

sealed interface SignalingMessage {
    val sessionId: String

    data class Join(
        override val sessionId: String,
        val role: String = "sender",
    ) : SignalingMessage

    data class Offer(
        override val sessionId: String,
        val sdp: String,
    ) : SignalingMessage

    data class Answer(
        override val sessionId: String,
        val sdp: String,
    ) : SignalingMessage

    data class IceCandidate(
        override val sessionId: String,
        val candidate: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
    ) : SignalingMessage

    data class Leave(
        override val sessionId: String,
    ) : SignalingMessage

    data class Error(
        override val sessionId: String,
        val code: String? = null,
        val message: String? = null,
    ) : SignalingMessage
}

interface SignalingSession : Closeable {
    val messages: Flow<SignalingMessage>

    suspend fun send(message: SignalingMessage)
}

fun interface SignalingClient {
    suspend fun open(config: StreamConfig): SignalingSession
}

sealed interface RtcSessionEvent {
    data class Status(
        val detail: UiText,
    ) : RtcSessionEvent

    data class Failure(
        val error: SenderError,
    ) : RtcSessionEvent

    data class CaptureInterrupted(
        val reason: String,
        val uiText: UiText = SenderError.CaptureInterrupted(reason).uiText,
    ) : RtcSessionEvent

    data class VideoProfileChanged(
        val activeProfile: VideoStreamProfile,
        val detail: UiText,
    ) : RtcSessionEvent

    data class VideoEncoderOverloaded(
        val error: SenderError.VideoEncoderOverloaded,
    ) : RtcSessionEvent

    data class AudioDegraded(
        val detail: UiText,
        val reason: String,
    ) : RtcSessionEvent

    data object Disconnected : RtcSessionEvent
}

data class PublishStartResult(
    val audioState: AudioState,
    val audioDetail: UiText? = null,
)

interface RtcPublishSession : Closeable {
    val events: Flow<RtcSessionEvent>

    suspend fun publish(projectionAccess: ProjectionAccess): PublishStartResult
}

fun interface RtcPublisherFactory {
    suspend fun createSession(
        config: StreamConfig,
        capabilities: CapabilitySnapshot,
        signalingSession: SignalingSession,
    ): RtcPublishSession
}
