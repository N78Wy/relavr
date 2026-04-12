package io.relavr.sender.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestHeadsetCameraSelectorTest {
    @Test
    fun `the camera tagged as rgb passthrough is preferred first`() {
        val selectedCameraId =
            QuestHeadsetCameraSelector.pickPreferredPassthroughCameraId(
                linkedMapOf(
                    "0" to null,
                    "1" to 2,
                    "rgb" to 0,
                ),
            )

        assertEquals("rgb", selectedCameraId)
    }

    @Test
    fun `null is returned when no passthrough tag is present`() {
        val selectedCameraId =
            QuestHeadsetCameraSelector.pickPreferredPassthroughCameraId(
                linkedMapOf(
                    "0" to null,
                    "1" to 2,
                ),
            )

        assertNull(selectedCameraId)
    }
}
