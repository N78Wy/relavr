package io.relavr.sender.testing.fakes

import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.AudioState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.session.CodecCapabilityRepository
import io.relavr.sender.core.session.CodecPolicy
import io.relavr.sender.core.session.PermissionDeniedException
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.ProjectionPermissionGateway
import io.relavr.sender.core.session.PublishStartResult
import io.relavr.sender.core.session.RecordAudioPermissionController
import io.relavr.sender.core.session.RecordAudioPermissionState
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

class FakeProjectionAccess : ProjectionAccess {
    var closed: Boolean = false

    override fun close() {
        closed = true
    }
}

class FakeRecordAudioPermissionController(
    initialState: RecordAudioPermissionState = RecordAudioPermissionState.Granted,
) : RecordAudioPermissionController {
    private val state = MutableStateFlow(initialState)

    var requestCount: Int = 0
    var openSettingsCount: Int = 0
    var nextRequestResult: RecordAudioPermissionState? = null

    override fun observeState(): StateFlow<RecordAudioPermissionState> = state

    override suspend fun requestPermissionIfNeeded(): RecordAudioPermissionState {
        requestCount += 1
        val result = nextRequestResult ?: state.value
        state.value = result
        nextRequestResult = null
        return result
    }

    override fun openAppSettings() {
        openSettingsCount += 1
    }

    fun updateState(newState: RecordAudioPermissionState) {
        state.value = newState
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

class FakeCodecCapabilityRepository(
    var snapshot: CapabilitySnapshot =
        CapabilitySnapshot(
            supportedCodecs = setOf(CodecPreference.H264),
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
    var nextResult = PublishStartResult(audioState = AudioState.Disabled)

    override val events: Flow<RtcSessionEvent> = eventFlow

    override suspend fun publish(projectionAccess: ProjectionAccess): PublishStartResult {
        if (shouldFail) {
            throw SenderException(SenderError.SessionStartFailed("fake-publish-failure"))
        }
        publishCount += 1
        lastProjectionAccess = projectionAccess
        return nextResult
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
    var lastCapabilities: CapabilitySnapshot? = null
    var lastSignalingSession: SignalingSession? = null

    override suspend fun createSession(
        config: StreamConfig,
        capabilities: CapabilitySnapshot,
        signalingSession: SignalingSession,
    ): RtcPublishSession {
        createCount += 1
        lastConfig = config
        lastCapabilities = capabilities
        lastSignalingSession = signalingSession
        return session
    }
}

class FakeAppLogger : AppLogger {
    data class InfoLog(
        val tag: String,
        val message: String,
    )

    data class ErrorLog(
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    val infoLogs = mutableListOf<InfoLog>()
    val errorLogs = mutableListOf<ErrorLog>()

    override fun info(
        tag: String,
        message: String,
    ) {
        infoLogs += InfoLog(tag = tag, message = message)
    }

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
                audioState =
                    if (config.audioEnabled) {
                        AudioState.Capturing
                    } else {
                        AudioState.Disabled
                    },
                resolvedConfig = config,
                activeVideoProfile = config.toVideoStreamProfile(),
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
