package io.relavr.sender.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal interface RecordAudioPermissionGateway {
    suspend fun requestPermissionIfNeeded(): Boolean
}

internal class AndroidRecordAudioPermissionGateway(
    private val appContext: Context,
) : RecordAudioPermissionGateway {
    private val _permissionRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var pendingRequest: CompletableDeferred<Boolean>? = null

    val permissionRequests: SharedFlow<String> = _permissionRequests.asSharedFlow()

    override suspend fun requestPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        check(pendingRequest == null) { "A record-audio permission request is already pending." }
        val deferred = CompletableDeferred<Boolean>()
        pendingRequest = deferred
        _permissionRequests.emit(Manifest.permission.RECORD_AUDIO)
        return deferred.await()
    }

    fun onPermissionResult(granted: Boolean) {
        val deferred = pendingRequest ?: return
        pendingRequest = null
        if (deferred.isActive) {
            deferred.complete(granted)
        }
    }
}
