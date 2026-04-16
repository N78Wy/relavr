package io.relavr.sender.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import io.relavr.sender.core.session.RecordAudioPermissionController
import io.relavr.sender.core.session.RecordAudioPermissionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

private const val RECORD_AUDIO_PERMISSION_STORE_NAME = "record_audio_permission"

private val Context.recordAudioPermissionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = RECORD_AUDIO_PERMISSION_STORE_NAME,
)

private val RECORD_AUDIO_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("record_audio.requested")

internal fun createRecordAudioPermissionGateway(context: Context): AndroidRecordAudioPermissionGateway =
    AndroidRecordAudioPermissionGateway(
        appContext = context.applicationContext,
        dataStore = context.recordAudioPermissionDataStore,
    )

internal class AndroidRecordAudioPermissionGateway(
    private val appContext: Context,
    private val dataStore: DataStore<Preferences>,
) : RecordAudioPermissionController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permissionState = MutableStateFlow(RecordAudioPermissionState.Requestable)
    private val permissionRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val appSettingsRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var pendingRequest: CompletableDeferred<RecordAudioPermissionState>? = null
    private var requestedBefore: Boolean = false

    init {
        scope.launch {
            requestedBefore = loadRequestedBefore()
        }
    }

    val permissionRequestFlow: SharedFlow<String> = permissionRequests.asSharedFlow()
    val appSettingsRequestFlow: SharedFlow<Unit> = appSettingsRequests.asSharedFlow()

    override fun observeState(): StateFlow<RecordAudioPermissionState> = permissionState

    override suspend fun requestPermissionIfNeeded(): RecordAudioPermissionState {
        when (permissionState.value) {
            RecordAudioPermissionState.Granted,
            RecordAudioPermissionState.PermanentlyDenied,
            -> return permissionState.value

            RecordAudioPermissionState.Requestable -> Unit
        }

        check(pendingRequest == null) { "A record-audio permission request is already pending." }
        markRequestedBefore()
        val deferred = CompletableDeferred<RecordAudioPermissionState>()
        pendingRequest = deferred
        permissionRequests.emit(Manifest.permission.RECORD_AUDIO)
        return deferred.await()
    }

    override fun openAppSettings() {
        appSettingsRequests.tryEmit(Unit)
    }

    fun syncPermissionStatus(shouldShowRationale: Boolean) {
        permissionState.value =
            computePermissionState(
                isGranted = hasRecordAudioPermission(),
                shouldShowRationale = shouldShowRationale,
            )
    }

    fun onPermissionResult(
        granted: Boolean,
        shouldShowRationale: Boolean,
    ) {
        val nextState =
            computePermissionState(
                isGranted = granted,
                shouldShowRationale = shouldShowRationale,
            )
        permissionState.value = nextState
        pendingRequest?.complete(nextState)
        pendingRequest = null
    }

    private fun computePermissionState(
        isGranted: Boolean,
        shouldShowRationale: Boolean,
    ): RecordAudioPermissionState =
        when {
            isGranted -> RecordAudioPermissionState.Granted
            requestedBefore && !shouldShowRationale -> RecordAudioPermissionState.PermanentlyDenied
            else -> RecordAudioPermissionState.Requestable
        }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun markRequestedBefore() {
        requestedBefore = true
        scope.launch {
            dataStore.edit { preferences ->
                preferences[RECORD_AUDIO_PERMISSION_REQUESTED_KEY] = true
            }
        }
    }

    private suspend fun loadRequestedBefore(): Boolean =
        dataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }.first()[RECORD_AUDIO_PERMISSION_REQUESTED_KEY] ?: false
}
