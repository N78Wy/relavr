package io.relavr.sender.testing.fakes

import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.session.AudioCaptureSource
import io.relavr.sender.core.session.AudioCaptureSourceFactory
import io.relavr.sender.core.session.CodecCapabilityRepository
import io.relavr.sender.core.session.CodecPolicy
import io.relavr.sender.core.session.PermissionDeniedException
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.ProjectionPermissionGateway
import io.relavr.sender.core.session.RtcPublishSession
import io.relavr.sender.core.session.RtcPublisherFactory
import io.relavr.sender.core.session.SenderException
import io.relavr.sender.core.session.SignalingClient
import io.relavr.sender.core.session.StreamingSessionController
import io.relavr.sender.core.session.VideoCaptureSource
import io.relavr.sender.core.session.VideoCaptureSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

class FakeVideoCaptureSource(
    override val description: String = "fake-video",
) : VideoCaptureSource {
    var closed: Boolean = false

    override fun close() {
        closed = true
    }
}

class FakeVideoCaptureSourceFactory(
    private val source: FakeVideoCaptureSource = FakeVideoCaptureSource(),
) : VideoCaptureSourceFactory {
    var createCount: Int = 0

    override suspend fun create(
        projectionAccess: ProjectionAccess,
        config: StreamConfig,
    ): VideoCaptureSource {
        createCount += 1
        return source
    }
}

class FakeAudioCaptureSource(
    override val enabled: Boolean = true,
) : AudioCaptureSource {
    var closed: Boolean = false

    override fun close() {
        closed = true
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
                    SenderError.CapabilityUnavailable("无可用编码格式"),
                )
            }
        return CodecSelection(
            requested = preference,
            resolved = resolved,
            fellBack = resolved != preference,
        )
    }
}

class FakeRtcPublishSession : RtcPublishSession {
    var publishCount: Int = 0
    var closed: Boolean = false
    var shouldFail: Boolean = false

    override suspend fun publish(
        videoSource: VideoCaptureSource,
        audioSource: AudioCaptureSource?,
    ) {
        if (shouldFail) {
            throw SenderException(SenderError.SessionStartFailed("fake-publish-failure"))
        }
        publishCount += 1
    }

    override fun close() {
        closed = true
    }
}

class FakeRtcPublisherFactory(
    val session: FakeRtcPublishSession = FakeRtcPublishSession(),
) : RtcPublisherFactory {
    var createCount: Int = 0

    override suspend fun createSession(config: StreamConfig): RtcPublishSession {
        createCount += 1
        return session
    }
}

class FakeSignalingClient : SignalingClient {
    var openCount: Int = 0
    var closeCount: Int = 0

    override suspend fun open(config: StreamConfig) {
        openCount += 1
    }

    override suspend fun closeSession() {
        closeCount += 1
    }

    override fun close() {
        closeCount += 1
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
        state.value = state.value.copy(resolvedConfig = config)
    }

    override suspend fun stop() {
        stopCount += 1
    }

    override fun observeState(): StateFlow<StreamingSessionSnapshot> = state

    fun updateState(snapshot: StreamingSessionSnapshot) {
        state.value = snapshot
    }
}
