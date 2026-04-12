package io.relavr.sender.core.model

data class VideoStreamProfile(
    val codecPreference: CodecPreference,
    val resolution: VideoResolution,
    val fps: Int,
    val bitrateKbps: Int,
) {
    val summaryLabel: String = "${resolution.label} / $fps FPS / $bitrateKbps kbps"

    companion object {
        fun from(config: StreamConfig): VideoStreamProfile =
            VideoStreamProfile(
                codecPreference = config.codecPreference,
                resolution = config.resolution,
                fps = config.fps,
                bitrateKbps = config.bitrateKbps,
            )
    }
}
