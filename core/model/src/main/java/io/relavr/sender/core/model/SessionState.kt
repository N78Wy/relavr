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

enum class AudioState {
    Disabled,
    Preparing,
    Capturing,
    VideoOnlyFallback,
    Degraded,
}

data class StreamingSessionSnapshot(
    val captureState: CaptureState = CaptureState.Idle,
    val publishState: PublishState = PublishState.Idle,
    val audioState: AudioState = AudioState.Disabled,
    val resolvedConfig: StreamConfig? = null,
    val activeVideoProfile: VideoStreamProfile? = null,
    val capabilities: CapabilitySnapshot? = null,
    val codecSelection: CodecSelection? = null,
    val statusDetail: UiText? = null,
    val audioDetail: UiText? = null,
    val error: SenderError? = null,
) {
    val isStreaming: Boolean =
        captureState == CaptureState.Capturing &&
            publishState == PublishState.Publishing
}
