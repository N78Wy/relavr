package io.relavr.sender.testing.fakes

import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.session.AudioCaptureSource
import io.relavr.sender.core.session.AudioCaptureSourceFactory
import io.relavr.sender.core.session.AudioFrameReadResult
import io.relavr.sender.core.session.CodecCapabilityRepository
import io.relavr.sender.core.session.CodecPolicy
import io.relavr.sender.core.session.PermissionDeniedException
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.ProjectionPermissionGateway
import io.relavr.sender.core.session.PublishStartResult
import io.relavr.sender.core.session.RtcPublishSession
import io.relavr.sender.core.session.RtcPublisherFactory
import io.relavr.sender.core.session.RtcSessionEvent
import io.relavr.sender.core.session.SenderException
import io.relavr.sender.core.session.SignalingClient
import io.relavr.sender.core.session.SignalingMessage
import io.relavr.sender.core.session.SignalingSession
import io.relavr.sender.core.session.StreamingSessionController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

class FakeProjectionAccess : ProjectionAccess {
    var closed: Boolean = false

    override fun close() {
        closed = true
    }
}

class FakeProjectionPermissionGateway(
    var restoredAccess: ProjectionAccess? = null,
    var nextAccess: ProjectionAccess = FakeProjectionAccess(),
    var shouldDeny: Boolean = false,
) : ProjectionPermissionGateway {
    var requestCount: Int = 0

    override suspend fun requestPermission(): ProjectionAccess {
        requestCount += 1
        if (shouldDeny) {
            throw PermissionDeniedException()
        }
        return nextAccess
    }

    override fun restoreIfAvailable(): ProjectionAccess? = restoredAccess
}

class FakeAudioCaptureSource(
    override val sampleRateHz: Int = 48_000,
    override val channelCount: Int = 2,
) : AudioCaptureSource {
    var closed: Boolean = false
    var started: Boolean = false
    var shouldReadFail: Boolean = false
    var nextBytesRead: Int = 0
    var nextTimestampNs: Long = 123L

    override fun close() {
        closed = true
    }

    override fun start(mediaProjection: android.media.projection.MediaProjection) {
        started = true
    }

    override fun read(
        targetBuffer: ByteBuffer,
        requestedBytes: Int,
    ): AudioFrameReadResult {
        if (shouldReadFail) {
            throw SenderException(SenderError.AudioCaptureUnavailable("fake-audio-read-failure"))
        }
        repeat(nextBytesRead.coerceAtMost(requestedBytes)) {
            targetBuffer.put(0x01.toByte())
        }
        return AudioFrameReadResult(
            bytesRead = nextBytesRead.coerceAtMost(requestedBytes),
            timestampNs = nextTimestampNs,
        )
    }
}

class FakeAudioCaptureSourceFactory(
    private val source: FakeAudioCaptureSource = FakeAudioCaptureSource(),
) : AudioCaptureSourceFactory {
    var createCount: Int = 0
    var shouldFail: Boolean = false

    override suspend fun create(
        projectionAccess: ProjectionAccess,
        config: StreamConfig,
    ): AudioCaptureSource? {
        createCount += 1
        if (shouldFail) {
            throw SenderException(SenderError.AudioCaptureUnavailable("fake-audio-failure"))
        }
        return source
    }
}

class FakeCodecCapabilityRepository(
    var snapshot: CapabilitySnapshot =
        CapabilitySnapshot(
            supportedCodecs = setOf(CodecPreference.H264),
            audioPlaybackCaptureSupported = true,
        ),
) : CodecCapabilityRepository {
    var requestCount: Int = 0

    override suspend fun getCapabilities(): CapabilitySnapshot {
        requestCount += 1
        return snapshot
    }
}

class FakeCodecPolicy : CodecPolicy {
    override fun select(
        preference: CodecPreference,
        capabilities: CapabilitySnapshot,
    ): CodecSelection {
        val resolved =
            if (capabilities.supports(preference)) {
                preference
            } else {
                capabilities.supportedCodecs.firstOrNull() ?: throw SenderException(
                    SenderError.CapabilityUnavailable("No video codec is available."),
                )
            }
        return CodecSelection(
            requested = preference,
            resolved = resolved,
            fellBack = resolved != preference,
        )
    }
}

class FakeSignalingSession : SignalingSession {
    private val incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 16)

    val sentMessages = mutableListOf<SignalingMessage>()
    var closeCount: Int = 0

    override val messages: Flow<SignalingMessage> = incomingMessages

    override suspend fun send(message: SignalingMessage) {
        sentMessages += message
    }

    fun emitIncoming(message: SignalingMessage) {
        incomingMessages.tryEmit(message)
    }

    override fun close() {
        closeCount += 1
    }
}

class FakeSignalingClient(
    val session: FakeSignalingSession = FakeSignalingSession(),
) : SignalingClient {
    var openCount: Int = 0
    var lastOpenedConfig: StreamConfig? = null

    override suspend fun open(config: StreamConfig): SignalingSession {
        openCount += 1
        lastOpenedConfig = config
        return session
    }
}

class FakeRtcPublishSession : RtcPublishSession {
    private val eventFlow = MutableSharedFlow<RtcSessionEvent>(extraBufferCapacity = 16)

    var publishCount: Int = 0
    var closed: Boolean = false
    var shouldFail: Boolean = false
    var lastProjectionAccess: ProjectionAccess? = null
    var lastAudioSource: AudioCaptureSource? = null
    var nextPublishResult: PublishStartResult? = null

    override val events: Flow<RtcSessionEvent> = eventFlow

    override suspend fun publish(
        projectionAccess: ProjectionAccess,
        audioSource: AudioCaptureSource?,
    ): PublishStartResult {
        if (shouldFail) {
            throw SenderException(SenderError.SessionStartFailed("fake-publish-failure"))
        }
        publishCount += 1
        lastProjectionAccess = projectionAccess
        lastAudioSource = audioSource
        return nextPublishResult
            ?: PublishStartResult(
                audioState =
                    if (audioSource != null) {
                        AudioStreamState.Publishing
                    } else {
                        AudioStreamState.Disabled
                    },
            )
    }

    fun emitEvent(event: RtcSessionEvent) {
        eventFlow.tryEmit(event)
    }

    override fun close() {
        closed = true
    }
}

class FakeRtcPublisherFactory(
    val session: FakeRtcPublishSession = FakeRtcPublishSession(),
) : RtcPublisherFactory {
    var createCount: Int = 0
    var lastConfig: StreamConfig? = null
    var lastSignalingSession: SignalingSession? = null

    override suspend fun createSession(
        config: StreamConfig,
        signalingSession: SignalingSession,
    ): RtcPublishSession {
        createCount += 1
        lastConfig = config
        lastSignalingSession = signalingSession
        return session
    }
}

class FakeAppLogger : AppLogger {
    data class ErrorLog(
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    val errorLogs = mutableListOf<ErrorLog>()

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        errorLogs += ErrorLog(tag = tag, message = message, throwable = throwable)
    }
}

class FakeStreamingSessionController(
    initialState: StreamingSessionSnapshot = StreamingSessionSnapshot(),
    private val capabilitySnapshot: CapabilitySnapshot =
        CapabilitySnapshot(
            supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
            audioPlaybackCaptureSupported = true,
        ),
) : StreamingSessionController {
    private val state = MutableStateFlow(initialState)

    var startCount: Int = 0
    var stopCount: Int = 0
    var lastStartConfig: StreamConfig? = null

    override suspend fun refreshCapabilities(): CapabilitySnapshot {
        state.value = state.value.copy(capabilities = capabilitySnapshot)
        return capabilitySnapshot
    }

    override suspend fun start(config: StreamConfig) {
        startCount += 1
        lastStartConfig = config
        state.value =
            state.value.copy(
                audioState = AudioStreamState.Disabled,
                resolvedConfig = config,
                statusDetail = UiText.of(io.relavr.sender.core.model.R.string.sender_status_default_idle),
            )
    }

    override suspend fun stop() {
        stopCount += 1
    }

    override fun observeState(): StateFlow<StreamingSessionSnapshot> = state

    fun updateState(snapshot: StreamingSessionSnapshot) {
        state.value = snapshot
    }
}
