package io.relavr.sender.app

import android.app.Application
import android.media.projection.MediaProjectionManager
import io.relavr.sender.core.common.DefaultAppDispatchers
import io.relavr.sender.core.session.StreamingSessionController
import io.relavr.sender.core.session.StreamingSessionCoordinator
import io.relavr.sender.feature.streamcontrol.StreamControlViewModelFactory
import io.relavr.sender.platform.androidcapture.AndroidProjectionPermissionGateway
import io.relavr.sender.platform.androidcapture.MediaProjectionVideoCaptureSourceFactory
import io.relavr.sender.platform.androidcapture.PlaybackAudioCaptureSourceFactory
import io.relavr.sender.platform.mediacodec.AndroidMediaCodecCapabilityRepository
import io.relavr.sender.platform.mediacodec.DefaultCodecPolicy
import io.relavr.sender.platform.webrtc.LoggingRtcPublisherFactory
import io.relavr.sender.platform.webrtc.NoOpSignalingClient

class AppContainer(
    application: Application,
) {
    private val mediaProjectionManager =
        application.getSystemService(MediaProjectionManager::class.java)

    val projectionPermissionGateway = AndroidProjectionPermissionGateway(mediaProjectionManager)

    private val sessionController: StreamingSessionController =
        StreamingSessionCoordinator(
            projectionPermissionGateway = projectionPermissionGateway,
            videoCaptureSourceFactory = MediaProjectionVideoCaptureSourceFactory(mediaProjectionManager),
            audioCaptureSourceFactory = PlaybackAudioCaptureSourceFactory(),
            codecCapabilityRepository = AndroidMediaCodecCapabilityRepository(DefaultAppDispatchers),
            codecPolicy = DefaultCodecPolicy(),
            rtcPublisherFactory = LoggingRtcPublisherFactory(),
            signalingClient = NoOpSignalingClient(),
            dispatchers = DefaultAppDispatchers,
        )

    val streamControlViewModelFactory = StreamControlViewModelFactory(sessionController)
}
