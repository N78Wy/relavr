package io.relavr.sender.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.relavr.sender.core.session.RecordAudioPermissionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPermissionGatewaysTest {
    private val recordAudioPermissionRequestedKey = booleanPreferencesKey("record_audio.requested")
    private val headsetCameraPermissionRequestedKey = booleanPreferencesKey("headset_camera.requested")

    @Test
    fun `record-audio gateway self-heals to granted after permission is restored in settings`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            var granted = false
            val gateway =
                AndroidRecordAudioPermissionGateway(
                    dataStore =
                        FakePreferencesDataStore(
                            mutablePreferencesOf(recordAudioPermissionRequestedKey to true),
                        ),
                    permissionChecker = { granted },
                    scope = scope,
                )

            scope.advanceUntilIdle()
            gateway.syncPermissionStatus(shouldShowRationale = false)
            assertEquals(RecordAudioPermissionState.PermanentlyDenied, gateway.observeState().value)

            granted = true
            assertEquals(RecordAudioPermissionState.Granted, gateway.requestPermissionIfNeeded())
            assertEquals(RecordAudioPermissionState.Granted, gateway.observeState().value)
        }

    @Test
    fun `headset-camera gateway marks permanent denial after the permission was requested before`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val gateway =
                AndroidHeadsetCameraPermissionGateway(
                    dataStore =
                        FakePreferencesDataStore(
                            mutablePreferencesOf(headsetCameraPermissionRequestedKey to true),
                        ),
                    permissionChecker = { false },
                    scope = scope,
                )

            scope.advanceUntilIdle()
            gateway.syncPermissionStatus(shouldShowRationale = false)

            assertEquals(HeadsetCameraPermissionState.PermanentlyDenied, gateway.observeState().value)
            assertEquals(HeadsetCameraPermissionState.PermanentlyDenied, gateway.requestPermissionIfNeeded())
        }

    @Test
    fun `headset-camera gateway self-heals to granted after permission is restored in settings`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            var granted = false
            val gateway =
                AndroidHeadsetCameraPermissionGateway(
                    dataStore =
                        FakePreferencesDataStore(
                            mutablePreferencesOf(headsetCameraPermissionRequestedKey to true),
                        ),
                    permissionChecker = { granted },
                    scope = scope,
                )

            scope.advanceUntilIdle()
            gateway.syncPermissionStatus(shouldShowRationale = false)
            assertEquals(HeadsetCameraPermissionState.PermanentlyDenied, gateway.observeState().value)

            granted = true
            assertEquals(HeadsetCameraPermissionState.Granted, gateway.requestPermissionIfNeeded())
            assertEquals(HeadsetCameraPermissionState.Granted, gateway.observeState().value)
        }

    private class FakePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updatedPreferences = transform(state.value)
            state.value = updatedPreferences
            return updatedPreferences
        }
    }
}
