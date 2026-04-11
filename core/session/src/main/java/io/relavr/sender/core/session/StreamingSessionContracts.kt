package io.relavr.sender.core.session

import android.content.Intent
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
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

interface AudioCaptureSource : Closeable {
    val enabled: Boolean
}

fun interface AudioCaptureSourceFactory {
    suspend fun create(
        projectionAccess: ProjectionAccess,
        config: StreamConfig,
    ): AudioCaptureSource?
}

interface CodecCapabilityRepository {
    suspend fun getCapabilities(): CapabilitySnapshot
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
        val message: String,
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
        val detail: String,
    ) : RtcSessionEvent

    data class Failure(
        val error: SenderError,
    ) : RtcSessionEvent

    data class CaptureInterrupted(
        val reason: String,
    ) : RtcSessionEvent

    data object Disconnected : RtcSessionEvent
}

interface RtcPublishSession : Closeable {
    val events: Flow<RtcSessionEvent>

    suspend fun publish(
        projectionAccess: ProjectionAccess,
        audioSource: AudioCaptureSource?,
    )
}

fun interface RtcPublisherFactory {
    suspend fun createSession(
        config: StreamConfig,
        signalingSession: SignalingSession,
    ): RtcPublishSession
}
