package io.relavr.sender.app

internal class FakeRecordAudioPermissionGateway : RecordAudioPermissionGateway {
    var requestCount: Int = 0
    var shouldThrow: Boolean = false
    var granted: Boolean = false
    var nextGranted: Boolean = true

    override fun hasPermission(): Boolean = granted

    override suspend fun requestPermissionIfNeeded(): Boolean {
        requestCount += 1
        if (shouldThrow) {
            throw IllegalStateException("fake-record-audio-permission-failure")
        }
        granted = nextGranted
        return nextGranted
    }
}
