package io.relavr.sender.core.session

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import kotlinx.coroutines.flow.StateFlow

interface StreamingSessionController {
    suspend fun refreshCapabilities(): CapabilitySnapshot

    suspend fun start(config: StreamConfig)

    suspend fun stop()

    fun observeState(): StateFlow<StreamingSessionSnapshot>
}
