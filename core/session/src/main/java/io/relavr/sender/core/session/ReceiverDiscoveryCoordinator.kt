package io.relavr.sender.core.session

import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.DiscoveredReceiver
import io.relavr.sender.core.model.ReceiverDiscoveryPhase
import io.relavr.sender.core.model.ReceiverDiscoverySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReceiverDiscoveryCoordinator(
    private val source: ReceiverDiscoverySource,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : ReceiverDiscoveryController {
    private val state = MutableStateFlow(ReceiverDiscoverySnapshot())
    private val sessionMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private var monitorJob: Job? = null
    private var started: Boolean = false
    private val receivers = linkedMapOf<String, DiscoveredReceiver>()

    override suspend fun start() {
        sessionMutex.withLock {
            if (started) {
                state.update { current ->
                    current.copy(
                        phase = ReceiverDiscoveryPhase.Discovering,
                        errorMessage = null,
                    )
                }
                return
            }

            state.update { current ->
                current.copy(
                    phase = ReceiverDiscoveryPhase.Discovering,
                    errorMessage = null,
                )
            }

            monitorJob = observeEvents()
            try {
                source.start()
                started = true
            } catch (throwable: Throwable) {
                monitorJob?.cancel()
                monitorJob = null
                logger.error(TAG, "启动局域网发现失败", throwable)
                state.update { current ->
                    current.copy(
                        phase = ReceiverDiscoveryPhase.Error,
                        errorMessage = throwable.message ?: "启动局域网发现失败",
                    )
                }
            }
        }
    }

    override suspend fun refresh() {
        sessionMutex.withLock {
            stopLocked()
            receivers.clear()
            state.value = ReceiverDiscoverySnapshot()
        }
        start()
    }

    override suspend fun stop() {
        sessionMutex.withLock {
            stopLocked()
            receivers.clear()
            state.value = ReceiverDiscoverySnapshot()
        }
    }

    override fun observeState(): StateFlow<ReceiverDiscoverySnapshot> = state

    private fun observeEvents(): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.events.collect { event ->
                when (event) {
                    is ReceiverDiscoveryEvent.Failure -> {
                        state.update { current ->
                            current.copy(
                                phase = ReceiverDiscoveryPhase.Error,
                                errorMessage = event.message,
                            )
                        }
                    }

                    is ReceiverDiscoveryEvent.Found -> {
                        receivers[event.receiver.serviceName] = event.receiver
                        state.update {
                            ReceiverDiscoverySnapshot(
                                phase = ReceiverDiscoveryPhase.Discovering,
                                receivers = receivers.values.sortedForUi(),
                                errorMessage = null,
                            )
                        }
                    }

                    is ReceiverDiscoveryEvent.Lost -> {
                        receivers.remove(event.serviceName)
                        state.update { current ->
                            current.copy(
                                phase = ReceiverDiscoveryPhase.Discovering,
                                receivers = receivers.values.sortedForUi(),
                            )
                        }
                    }
                }
            }
        }

    private suspend fun stopLocked() {
        if (!started) {
            return
        }
        started = false
        monitorJob?.cancel()
        monitorJob = null
        runCatching { source.stop() }
            .onFailure { throwable ->
                logger.error(TAG, "停止局域网发现失败", throwable)
            }
    }

    private fun Collection<DiscoveredReceiver>.sortedForUi(): List<DiscoveredReceiver> =
        sortedWith(
            compareBy<DiscoveredReceiver> { it.receiverName.lowercase() }
                .thenBy { it.sessionId.lowercase() }
                .thenBy { it.endpoint.lowercase() },
        )

    private companion object {
        const val TAG = "ReceiverDiscovery"
    }
}
