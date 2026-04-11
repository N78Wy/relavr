package io.relavr.sender.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestHeadsetCameraSelectorTest {
    @Test
    fun `优先选择 vendor tag 标记为 rgb passthrough 的相机`() {
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
    fun `没有 passthrough 标记时返回空`() {
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
