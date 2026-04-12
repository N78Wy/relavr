package io.relavr.sender.app

import io.relavr.sender.feature.streamcontrol.RecordAudioPermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordAudioPermissionStatusResolverTest {
    @Test
    fun `granted permission resolves to granted`() {
        assertEquals(
            RecordAudioPermissionStatus.Granted,
            resolveRecordAudioPermissionStatus(
                granted = true,
                hasRequestedBefore = true,
                shouldShowRequestPermissionRationale = false,
            ),
        )
    }

    @Test
    fun `never requested permission stays requestable`() {
        assertEquals(
            RecordAudioPermissionStatus.Requestable,
            resolveRecordAudioPermissionStatus(
                granted = false,
                hasRequestedBefore = false,
                shouldShowRequestPermissionRationale = false,
            ),
        )
    }

    @Test
    fun `ordinary denial remains requestable`() {
        assertEquals(
            RecordAudioPermissionStatus.Requestable,
            resolveRecordAudioPermissionStatus(
                granted = false,
                hasRequestedBefore = true,
                shouldShowRequestPermissionRationale = true,
            ),
        )
    }

    @Test
    fun `denied and no longer requestable becomes permanently denied`() {
        assertEquals(
            RecordAudioPermissionStatus.PermanentlyDenied,
            resolveRecordAudioPermissionStatus(
                granted = false,
                hasRequestedBefore = true,
                shouldShowRequestPermissionRationale = false,
            ),
        )
    }
}
