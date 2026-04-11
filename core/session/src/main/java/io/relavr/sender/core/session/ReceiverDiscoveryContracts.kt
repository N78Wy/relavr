package io.relavr.sender.core.session

import io.relavr.sender.core.model.DiscoveredReceiver
import io.relavr.sender.core.model.ReceiverDiscoverySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ReceiverDiscoveryEvent {
    data class Found(
        val receiver: DiscoveredReceiver,
    ) : ReceiverDiscoveryEvent

    data class Lost(
        val serviceName: String,
    ) : ReceiverDiscoveryEvent

    data class Failure(
        val message: String,
    ) : ReceiverDiscoveryEvent
}

interface ReceiverDiscoverySource {
    val events: Flow<ReceiverDiscoveryEvent>

    suspend fun start()

    suspend fun stop()
}

interface ReceiverDiscoveryController {
    suspend fun start()

    suspend fun refresh()

    suspend fun stop()

    fun observeState(): StateFlow<ReceiverDiscoverySnapshot>
}
