package io.relavr.sender.platform.androidcapture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import io.relavr.sender.core.session.PermissionDeniedException
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.ProjectionPermissionGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidProjectionPermissionGateway(
    private val mediaProjectionManager: MediaProjectionManager,
) : ProjectionPermissionGateway {
    private val _permissionRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    private var pendingRequest: CompletableDeferred<ProjectionAccess>? = null
    private var cachedAccess: AndroidProjectionAccess? = null

    val permissionRequests: SharedFlow<Intent> = _permissionRequests.asSharedFlow()

    override suspend fun requestPermission(): ProjectionAccess {
        check(pendingRequest == null) { "已有待处理的屏幕采集授权请求" }
        val deferred = CompletableDeferred<ProjectionAccess>()
        pendingRequest = deferred
        _permissionRequests.emit(mediaProjectionManager.createScreenCaptureIntent())
        return deferred.await()
    }

    override fun restoreIfAvailable(): ProjectionAccess? = cachedAccess?.copyWithFreshIntent()

    fun onPermissionResult(
        resultCode: Int,
        data: Intent?,
    ) {
        val deferred = pendingRequest ?: return
        pendingRequest = null

        if (!deferred.isActive) {
            return
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val access =
                AndroidProjectionAccess(
                    resultCode = resultCode,
                    resultData = Intent(data),
                )
            cachedAccess = access
            deferred.complete(access.copyWithFreshIntent())
        } else {
            deferred.completeExceptionally(PermissionDeniedException())
        }
    }
}

data class AndroidProjectionAccess(
    val resultCode: Int,
    val resultData: Intent,
) : ProjectionAccess {
    override fun close() = Unit

    fun copyWithFreshIntent(): AndroidProjectionAccess = copy(resultData = Intent(resultData))
}
