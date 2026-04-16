package io.relavr.sender.core.session

import kotlinx.coroutines.flow.StateFlow

enum class RecordAudioPermissionState {
    Granted,
    Requestable,
    PermanentlyDenied,
}

interface RecordAudioPermissionController {
    fun observeState(): StateFlow<RecordAudioPermissionState>

    suspend fun requestPermissionIfNeeded(): RecordAudioPermissionState

    fun openAppSettings()
}
