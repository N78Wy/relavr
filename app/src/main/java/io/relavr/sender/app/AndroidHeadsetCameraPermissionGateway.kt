package io.relavr.sender.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
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

internal enum class HeadsetCameraPermissionState {
    Granted,
    Requestable,
    PermanentlyDenied,
}

private const val HEADSET_CAMERA_PERMISSION = "horizonos.permission.HEADSET_CAMERA"
private const val HEADSET_CAMERA_PERMISSION_STORE_NAME = "headset_camera_permission"

private val Context.headsetCameraPermissionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = HEADSET_CAMERA_PERMISSION_STORE_NAME,
)

private val HEADSET_CAMERA_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("headset_camera.requested")

internal fun createHeadsetCameraPermissionGateway(context: Context): AndroidHeadsetCameraPermissionGateway =
    AndroidHeadsetCameraPermissionGateway(
        dataStore = context.headsetCameraPermissionDataStore,
        permissionChecker = {
            ContextCompat.checkSelfPermission(context, HEADSET_CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED
        },
    )

internal class AndroidHeadsetCameraPermissionGateway(
    private val dataStore: DataStore<Preferences>,
    private val permissionChecker: () -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val permissionState = MutableStateFlow(HeadsetCameraPermissionState.Requestable)
    private val permissionRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val appSettingsRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var pendingRequest: CompletableDeferred<HeadsetCameraPermissionState>? = null
    private var requestedBefore: Boolean = false

    init {
        scope.launch {
            requestedBefore = loadRequestedBefore()
        }
    }

    val permissionRequestFlow: SharedFlow<String> = permissionRequests.asSharedFlow()
    val appSettingsRequestFlow: SharedFlow<Unit> = appSettingsRequests.asSharedFlow()

    fun observeState(): StateFlow<HeadsetCameraPermissionState> = permissionState

    suspend fun requestPermissionIfNeeded(): HeadsetCameraPermissionState {
        if (permissionChecker()) {
            permissionState.value = HeadsetCameraPermissionState.Granted
            return HeadsetCameraPermissionState.Granted
        }
        if (permissionState.value == HeadsetCameraPermissionState.Granted) {
            permissionState.value = HeadsetCameraPermissionState.Requestable
        }
        if (permissionState.value == HeadsetCameraPermissionState.PermanentlyDenied) {
            return HeadsetCameraPermissionState.PermanentlyDenied
        }

        check(pendingRequest == null) { "A headset-camera permission request is already pending." }
        markRequestedBefore()
        val deferred = CompletableDeferred<HeadsetCameraPermissionState>()
        pendingRequest = deferred
        permissionRequests.emit(HEADSET_CAMERA_PERMISSION)
        return deferred.await()
    }

    fun openAppSettings() {
        appSettingsRequests.tryEmit(Unit)
    }

    fun syncPermissionStatus(shouldShowRationale: Boolean) {
        permissionState.value =
            computePermissionState(
                isGranted = permissionChecker(),
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
    ): HeadsetCameraPermissionState =
        when {
            isGranted -> HeadsetCameraPermissionState.Granted
            requestedBefore && !shouldShowRationale -> HeadsetCameraPermissionState.PermanentlyDenied
            else -> HeadsetCameraPermissionState.Requestable
        }

    private fun markRequestedBefore() {
        requestedBefore = true
        scope.launch {
            dataStore.edit { preferences ->
                preferences[HEADSET_CAMERA_PERMISSION_REQUESTED_KEY] = true
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
            }.first()[HEADSET_CAMERA_PERMISSION_REQUESTED_KEY] ?: false
}
