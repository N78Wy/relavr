package io.relavr.sender.app

import io.relavr.sender.core.model.StreamConfig

class FakeForegroundServiceCommandDispatcher : ForegroundServiceCommandDispatcher {
    var startCount: Int = 0
    var stopCount: Int = 0
    var lastStartConfig: StreamConfig? = null
    var startFailure: Throwable? = null
    var stopFailure: Throwable? = null

    override fun startSession(config: StreamConfig) {
        startFailure?.let { throw it }
        startCount += 1
        lastStartConfig = config
    }

    override fun stopSession() {
        stopFailure?.let { throw it }
        stopCount += 1
    }
}
