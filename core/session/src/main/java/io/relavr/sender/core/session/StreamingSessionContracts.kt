package io.relavr.sender.core.session

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.StreamConfig
import java.io.Closeable

interface ProjectionAccess : Closeable

interface ProjectionPermissionGateway {
    suspend fun requestPermission(): ProjectionAccess

    fun restoreIfAvailable(): ProjectionAccess?
}

interface VideoCaptureSource : Closeable {
    val description: String
}

fun interface VideoCaptureSourceFactory {
    suspend fun create(
        projectionAccess: ProjectionAccess,
        config: StreamConfig,
    ): VideoCaptureSource
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
        preference: io.relavr.sender.core.model.CodecPreference,
        capabilities: CapabilitySnapshot,
    ): io.relavr.sender.core.model.CodecSelection
}

interface RtcPublishSession : Closeable {
    suspend fun publish(
        videoSource: VideoCaptureSource,
        audioSource: AudioCaptureSource?,
    )
}

fun interface RtcPublisherFactory {
    suspend fun createSession(config: StreamConfig): RtcPublishSession
}

interface SignalingClient : Closeable {
    suspend fun open(config: StreamConfig)

    suspend fun closeSession()
}
