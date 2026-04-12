package io.relavr.sender.app

import io.relavr.sender.feature.streamcontrol.RecordAudioPermissionStatus

internal fun resolveRecordAudioPermissionStatus(
    granted: Boolean,
    hasRequestedBefore: Boolean,
    shouldShowRequestPermissionRationale: Boolean,
): RecordAudioPermissionStatus =
    when {
        granted -> RecordAudioPermissionStatus.Granted
        hasRequestedBefore.not() || shouldShowRequestPermissionRationale -> RecordAudioPermissionStatus.Requestable
        else -> RecordAudioPermissionStatus.PermanentlyDenied
    }
