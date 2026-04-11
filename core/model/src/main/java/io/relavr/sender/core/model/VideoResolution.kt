package io.relavr.sender.core.model

data class VideoResolution(
    val width: Int,
    val height: Int,
) {
    val label: String = "${width}x$height"
}
