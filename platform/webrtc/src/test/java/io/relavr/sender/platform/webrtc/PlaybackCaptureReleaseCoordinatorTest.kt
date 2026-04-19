package io.relavr.sender.platform.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackCaptureReleaseCoordinatorTest {
    @Test
    fun `clear immediately closes the installed session when audio thread is idle`() {
        val coordinator = PlaybackCaptureReleaseCoordinator<String>()

        assertEquals(
            PlaybackCaptureReleaseAction<String>(),
            coordinator.install("session-1"),
        )

        val action = coordinator.clear()

        assertEquals(
            PlaybackCaptureReleaseAction(
                sessionsToClose = listOf("session-1"),
                clearAudioInput = true,
            ),
            action,
        )
        assertNull(coordinator.currentSession())
    }

    @Test
    fun `clear defers closing the installed session until audio thread stops`() {
        val coordinator = PlaybackCaptureReleaseCoordinator<String>()

        coordinator.install("session-1")
        coordinator.onAudioRecordStarted()

        assertEquals(
            PlaybackCaptureReleaseAction<String>(),
            coordinator.clear(),
        )
        assertEquals("session-1", coordinator.currentSession())

        val stopAction = coordinator.onAudioRecordStopped()

        assertEquals(
            PlaybackCaptureReleaseAction(
                sessionsToClose = listOf("session-1"),
                clearAudioInput = true,
            ),
            stopAction,
        )
        assertNull(coordinator.currentSession())
    }

    @Test
    fun `releaseAll closes pending playback capture even if stop callback has not arrived yet`() {
        val coordinator = PlaybackCaptureReleaseCoordinator<String>()

        coordinator.install("session-1")
        coordinator.onAudioRecordStarted()
        coordinator.clear()

        val action = coordinator.releaseAll()

        assertEquals(
            PlaybackCaptureReleaseAction(
                sessionsToClose = listOf("session-1"),
                clearAudioInput = true,
            ),
            action,
        )
        assertNull(coordinator.currentSession())
    }

    @Test
    fun `install rejects replacing playback capture while audio thread is still running`() {
        val coordinator = PlaybackCaptureReleaseCoordinator<String>()

        coordinator.install("session-1")
        coordinator.onAudioRecordStarted()

        val error =
            assertThrows(IllegalStateException::class.java) {
                coordinator.install("session-2")
            }

        assertEquals(
            "Cannot replace playback capture while the WebRTC audio thread is still running.",
            error.message,
        )
    }
}
