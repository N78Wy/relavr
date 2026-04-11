package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.session.SignalingClient

class NoOpSignalingClient : SignalingClient {
    private var openedEndpoint: String? = null

    override suspend fun open(config: StreamConfig) {
        openedEndpoint = config.signalingEndpoint
    }

    override suspend fun closeSession() {
        openedEndpoint = null
    }

    override fun close() {
        openedEndpoint = null
    }
}
