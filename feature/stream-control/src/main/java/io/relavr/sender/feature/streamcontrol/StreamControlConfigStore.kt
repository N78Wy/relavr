package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.StreamConfig

interface StreamControlConfigStore {
    suspend fun load(): StreamConfig

    suspend fun save(config: StreamConfig)
}
