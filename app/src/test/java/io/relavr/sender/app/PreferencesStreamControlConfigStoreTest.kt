package io.relavr.sender.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PreferencesStreamControlConfigStoreTest {
    @Test
    fun `save followed by load restores all editable fields`() =
        runTest {
            val dataStore = FakePreferencesDataStore()
            val configStore = PreferencesStreamControlConfigStore(dataStore)
            val expected =
                StreamConfig(
                    signalingEndpoint = "wss://signal.example/ws",
                    sessionId = "room-42",
                    codecPreference = CodecPreference.HEVC,
                    resolution = VideoResolution(width = 1920, height = 1080),
                    fps = 60,
                    bitrateKbps = 8000,
                )

            configStore.save(expected)

            assertEquals(expected, configStore.load())
        }

    @Test
    fun `load falls back to defaults when stored values are missing or invalid`() =
        runTest {
            val dataStore =
                FakePreferencesDataStore(
                    mutablePreferencesOf(
                        CODEC_PREFERENCE_KEY to "NOT_REAL",
                        RESOLUTION_WIDTH_KEY to 1111,
                        RESOLUTION_HEIGHT_KEY to 777,
                        FPS_KEY to 120,
                        BITRATE_KBPS_KEY to 9999,
                    ),
                )
            val configStore = PreferencesStreamControlConfigStore(dataStore)

            val loadedConfig = configStore.load()

            assertEquals(StreamConfig.DEFAULT_SIGNALING_ENDPOINT, loadedConfig.signalingEndpoint)
            assertEquals(StreamConfig.DEFAULT_SESSION_ID, loadedConfig.sessionId)
            assertEquals(StreamConfig().codecPreference, loadedConfig.codecPreference)
            assertEquals(StreamConfig.DEFAULT_RESOLUTION, loadedConfig.resolution)
            assertEquals(StreamConfig.DEFAULT_FPS, loadedConfig.fps)
            assertEquals(StreamConfig.DEFAULT_BITRATE_KBPS, loadedConfig.bitrateKbps)
        }

    @Test
    fun `load returns defaults when datastore read fails with io exception`() =
        runTest {
            val configStore =
                PreferencesStreamControlConfigStore(
                    FailingPreferencesDataStore(IOException("boom")),
                )

            assertEquals(StreamConfig(), configStore.load())
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

    private class FailingPreferencesDataStore(
        private val throwable: Throwable,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> =
            flow {
                throw throwable
            }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw throwable
    }
}
