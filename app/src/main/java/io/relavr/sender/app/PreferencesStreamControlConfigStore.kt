package io.relavr.sender.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.feature.streamcontrol.StreamControlConfigStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val STREAM_CONTROL_CONFIG_STORE_NAME = "stream_control_config"

private val Context.streamControlConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = STREAM_CONTROL_CONFIG_STORE_NAME,
)

private val defaultStreamConfig = StreamConfig()

internal fun createStreamControlConfigStore(context: Context): StreamControlConfigStore =
    PreferencesStreamControlConfigStore(context.streamControlConfigDataStore)

internal class PreferencesStreamControlConfigStore(
    private val dataStore: DataStore<Preferences>,
) : StreamControlConfigStore {
    override suspend fun load(): StreamConfig {
        val preferences =
            dataStore.data
                .catch { throwable ->
                    if (throwable is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw throwable
                    }
                }.first()
        return preferences.toStreamConfig()
    }

    override suspend fun save(config: StreamConfig) {
        dataStore.edit { preferences ->
            preferences.updateFrom(config)
        }
    }
}

internal val SIGNALING_ENDPOINT_KEY = stringPreferencesKey("stream_control.signaling_endpoint")
internal val SESSION_ID_KEY = stringPreferencesKey("stream_control.session_id")
internal val AUDIO_ENABLED_KEY = booleanPreferencesKey("stream_control.audio_enabled")
internal val CODEC_PREFERENCE_KEY = stringPreferencesKey("stream_control.codec_preference")
internal val RESOLUTION_WIDTH_KEY = intPreferencesKey("stream_control.resolution_width")
internal val RESOLUTION_HEIGHT_KEY = intPreferencesKey("stream_control.resolution_height")
internal val FPS_KEY = intPreferencesKey("stream_control.fps")
internal val BITRATE_KBPS_KEY = intPreferencesKey("stream_control.bitrate_kbps")

private fun Preferences.toStreamConfig(): StreamConfig =
    StreamConfig(
        signalingEndpoint = this[SIGNALING_ENDPOINT_KEY] ?: defaultStreamConfig.signalingEndpoint,
        sessionId = this[SESSION_ID_KEY] ?: defaultStreamConfig.sessionId,
        audioEnabled = this[AUDIO_ENABLED_KEY] ?: defaultStreamConfig.audioEnabled,
        codecPreference =
            CodecPreference.entries.firstOrNull { preference ->
                preference.name == this[CODEC_PREFERENCE_KEY]
            } ?: defaultStreamConfig.codecPreference,
        resolution =
            StreamConfig.RESOLUTION_OPTIONS.firstOrNull { resolution ->
                resolution.width == this[RESOLUTION_WIDTH_KEY] &&
                    resolution.height == this[RESOLUTION_HEIGHT_KEY]
            } ?: defaultStreamConfig.resolution,
        fps = this[FPS_KEY]?.takeIf { storedFps -> storedFps in StreamConfig.FPS_OPTIONS } ?: defaultStreamConfig.fps,
        bitrateKbps =
            this[BITRATE_KBPS_KEY]?.takeIf { storedBitrate ->
                storedBitrate in StreamConfig.BITRATE_OPTIONS_KBPS
            } ?: defaultStreamConfig.bitrateKbps,
    )

private fun androidx.datastore.preferences.core.MutablePreferences.updateFrom(config: StreamConfig) {
    this[SIGNALING_ENDPOINT_KEY] = config.signalingEndpoint
    this[SESSION_ID_KEY] = config.sessionId
    this[AUDIO_ENABLED_KEY] = config.audioEnabled
    this[CODEC_PREFERENCE_KEY] = config.codecPreference.name
    this[RESOLUTION_WIDTH_KEY] = config.resolution.width
    this[RESOLUTION_HEIGHT_KEY] = config.resolution.height
    this[FPS_KEY] = config.fps
    this[BITRATE_KBPS_KEY] = config.bitrateKbps
}
