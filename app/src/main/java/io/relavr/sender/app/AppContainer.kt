package io.relavr.sender.app

import android.app.Application
import android.media.projection.MediaProjectionManager
import io.relavr.sender.core.common.AndroidAppLogger
import io.relavr.sender.core.common.DefaultAppDispatchers
import io.relavr.sender.core.session.StreamingSessionController
import io.relavr.sender.core.session.StreamingSessionCoordinator
import io.relavr.sender.feature.streamcontrol.StreamControlViewModelFactory
import io.relavr.sender.platform.androidcapture.AndroidProjectionPermissionGateway
import io.relavr.sender.platform.androidcapture.PlaybackAudioCaptureSourceFactory
import io.relavr.sender.platform.mediacodec.AndroidMediaCodecCapabilityRepository
import io.relavr.sender.platform.mediacodec.DefaultCodecPolicy
import io.relavr.sender.platform.webrtc.DefaultWebRtcCodecSupportProvider
import io.relavr.sender.platform.webrtc.WebRtcLibraryInitializer
import io.relavr.sender.platform.webrtc.WebRtcPublisherFactory
import io.relavr.sender.platform.webrtc.WebSocketSignalingClient

class AppContainer(
    application: Application,
) {
    private val mediaProjectionManager =
        application.getSystemService(MediaProjectionManager::class.java)
    private val webRtcLibraryInitializer = WebRtcLibraryInitializer.create(application)

    val projectionPermissionGateway = AndroidProjectionPermissionGateway(mediaProjectionManager)
    internal val recordAudioPermissionGateway = AndroidRecordAudioPermissionGateway(application)
    internal val recordAudioPermissionPreferenceStore = createRecordAudioPermissionPreferenceStore(application)

    internal val sessionEngine: StreamingSessionController =
        StreamingSessionCoordinator(
            projectionPermissionGateway = projectionPermissionGateway,
            audioCaptureSourceFactory = PlaybackAudioCaptureSourceFactory(application),
            codecCapabilityRepository =
                CombinedCodecCapabilityRepository(
                    androidCapabilityRepository = AndroidMediaCodecCapabilityRepository(DefaultAppDispatchers),
                    webRtcCodecSupportProvider =
                        DefaultWebRtcCodecSupportProvider(webRtcLibraryInitializer),
                ),
            codecPolicy = DefaultCodecPolicy(),
            rtcPublisherFactory =
                WebRtcPublisherFactory(application, webRtcLibraryInitializer, AndroidAppLogger),
            signalingClient = WebSocketSignalingClient(logger = AndroidAppLogger),
            dispatchers = DefaultAppDispatchers,
            logger = AndroidAppLogger,
        )

    private val foregroundServiceCommandDispatcher =
        AndroidForegroundServiceCommandDispatcher(application)
    private val streamControlConfigStore = createStreamControlConfigStore(application)

    private val sessionController: StreamingSessionController =
        ForegroundServiceStreamingSessionController(
            sessionEngine = sessionEngine,
            commandDispatcher = foregroundServiceCommandDispatcher,
            recordAudioPermissionGateway = recordAudioPermissionGateway,
            dispatchers = DefaultAppDispatchers,
            logger = AndroidAppLogger,
        )

    val streamControlViewModelFactory =
        StreamControlViewModelFactory(
            sessionController = sessionController,
            configStore = streamControlConfigStore,
        )
}
