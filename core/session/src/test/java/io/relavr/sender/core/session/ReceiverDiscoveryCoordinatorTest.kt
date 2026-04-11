package io.relavr.sender.core.session

import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.model.DiscoveredReceiver
import io.relavr.sender.core.model.ReceiverDiscoveryPhase
import io.relavr.sender.testing.fakes.FakeAppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverDiscoveryCoordinatorTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers =
        object : AppDispatchers {
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }

    @Test
    fun `start 后会进入发现态并接收服务列表`() =
        runTest(dispatcher.scheduler) {
            val source = FakeReceiverDiscoverySource()
            val coordinator = ReceiverDiscoveryCoordinator(source, dispatchers, FakeAppLogger())

            coordinator.start()
            source.emit(
                ReceiverDiscoveryEvent.Found(
                    DiscoveredReceiver(
                        serviceName = "bedroom",
                        receiverName = "Bedroom",
                        sessionId = "room-2",
                        host = "192.168.1.22",
                        port = 17888,
                        authRequired = false,
                    ),
                ),
            )
            source.emit(
                ReceiverDiscoveryEvent.Found(
                    DiscoveredReceiver(
                        serviceName = "living-room",
                        receiverName = "Living Room",
                        sessionId = "room-1",
                        host = "192.168.1.21",
                        port = 17888,
                        authRequired = true,
                    ),
                ),
            )
            dispatcher.scheduler.advanceUntilIdle()

            val snapshot = coordinator.observeState().value
            assertEquals(1, source.startCount)
            assertEquals(ReceiverDiscoveryPhase.Discovering, snapshot.phase)
            assertEquals(listOf("Bedroom", "Living Room"), snapshot.receivers.map { it.receiverName })
            assertNull(snapshot.errorMessage)
        }

    @Test
    fun `lost 事件会移除对应服务`() =
        runTest(dispatcher.scheduler) {
            val source = FakeReceiverDiscoverySource()
            val coordinator = ReceiverDiscoveryCoordinator(source, dispatchers, FakeAppLogger())

            coordinator.start()
            source.emit(
                ReceiverDiscoveryEvent.Found(
                    DiscoveredReceiver(
                        serviceName = "living-room",
                        receiverName = "Living Room",
                        sessionId = "room-1",
                        host = "192.168.1.21",
                        port = 17888,
                        authRequired = false,
                    ),
                ),
            )
            source.emit(ReceiverDiscoveryEvent.Lost(serviceName = "living-room"))
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(
                coordinator
                    .observeState()
                    .value.receivers
                    .isEmpty(),
            )
        }

    @Test
    fun `refresh 会重新启动发现并清空旧列表`() =
        runTest(dispatcher.scheduler) {
            val source = FakeReceiverDiscoverySource()
            val coordinator = ReceiverDiscoveryCoordinator(source, dispatchers, FakeAppLogger())

            coordinator.start()
            source.emit(
                ReceiverDiscoveryEvent.Found(
                    DiscoveredReceiver(
                        serviceName = "living-room",
                        receiverName = "Living Room",
                        sessionId = "room-1",
                        host = "192.168.1.21",
                        port = 17888,
                        authRequired = false,
                    ),
                ),
            )
            dispatcher.scheduler.advanceUntilIdle()

            coordinator.refresh()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, source.startCount)
            assertEquals(1, source.stopCount)
            assertTrue(
                coordinator
                    .observeState()
                    .value.receivers
                    .isEmpty(),
            )
        }

    @Test
    fun `failure 事件会进入错误态但保留已发现列表`() =
        runTest(dispatcher.scheduler) {
            val source = FakeReceiverDiscoverySource()
            val coordinator = ReceiverDiscoveryCoordinator(source, dispatchers, FakeAppLogger())

            coordinator.start()
            source.emit(
                ReceiverDiscoveryEvent.Found(
                    DiscoveredReceiver(
                        serviceName = "living-room",
                        receiverName = "Living Room",
                        sessionId = "room-1",
                        host = "192.168.1.21",
                        port = 17888,
                        authRequired = false,
                    ),
                ),
            )
            source.emit(ReceiverDiscoveryEvent.Failure(message = "mdns failed"))
            dispatcher.scheduler.advanceUntilIdle()

            val snapshot = coordinator.observeState().value
            assertEquals(ReceiverDiscoveryPhase.Error, snapshot.phase)
            assertEquals("mdns failed", snapshot.errorMessage)
            assertEquals(1, snapshot.receivers.size)
        }

    @Test
    fun `stop 会停止发现并重置状态`() =
        runTest(dispatcher.scheduler) {
            val source = FakeReceiverDiscoverySource()
            val coordinator = ReceiverDiscoveryCoordinator(source, dispatchers, FakeAppLogger())

            coordinator.start()
            dispatcher.scheduler.advanceUntilIdle()

            coordinator.stop()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, source.stopCount)
            assertEquals(ReceiverDiscoveryPhase.Idle, coordinator.observeState().value.phase)
            assertTrue(
                coordinator
                    .observeState()
                    .value.receivers
                    .isEmpty(),
            )
        }
}

private class FakeReceiverDiscoverySource : ReceiverDiscoverySource {
    private val eventFlow = MutableSharedFlow<ReceiverDiscoveryEvent>(extraBufferCapacity = 16)

    var startCount: Int = 0
    var stopCount: Int = 0

    override val events: Flow<ReceiverDiscoveryEvent> = eventFlow

    override suspend fun start() {
        startCount += 1
    }

    override suspend fun stop() {
        stopCount += 1
    }

    fun emit(event: ReceiverDiscoveryEvent) {
        eventFlow.tryEmit(event)
    }
}
