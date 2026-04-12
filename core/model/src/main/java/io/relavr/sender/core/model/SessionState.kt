package io.relavr.sender.core.model

enum class CaptureState {
    Idle,
    RequestingPermission,
    Starting,
    Capturing,
    Stopping,
    Error,
}

enum class PublishState {
    Idle,
    Preparing,
    Publishing,
    Stopping,
    Error,
}

data class StreamingSessionSnapshot(
    val captureState: CaptureState = CaptureState.Idle,
    val publishState: PublishState = PublishState.Idle,
    val resolvedConfig: StreamConfig? = null,
    val activeVideoProfile: VideoStreamProfile? = null,
    val capabilities: CapabilitySnapshot? = null,
    val codecSelection: CodecSelection? = null,
    val statusDetail: UiText? = null,
    val error: SenderError? = null,
) {
    val isStreaming: Boolean =
        captureState == CaptureState.Capturing &&
            publishState == PublishState.Publishing
}
