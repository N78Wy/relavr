package io.relavr.sender.platform.webrtc

internal object SdpCodecPreference {
    fun preferVideoCodec(
        sdpDescription: String,
        codecName: String,
    ): String {
        val lines = sdpDescription.split(LINE_SEPARATOR).toMutableList()
        val videoLineIndex = lines.indexOfFirst { it.startsWith("m=video ") }
        if (videoLineIndex == -1) {
            return sdpDescription
        }

        val preferredPayloads = collectCodecPayloads(lines, codecName)
        if (preferredPayloads.isEmpty()) {
            return sdpDescription
        }

        val reorderedMLine = reorderVideoMLine(lines[videoLineIndex], preferredPayloads)
        lines[videoLineIndex] = reorderedMLine
        return lines.joinToString(LINE_SEPARATOR)
    }

    private fun collectCodecPayloads(
        lines: List<String>,
        codecName: String,
    ): List<String> {
        val rtpMapRegex = Regex("^a=rtpmap:(\\d+) ${Regex.escape(codecName)}(?:/\\d+)+$", RegexOption.IGNORE_CASE)
        val fmtpRegex = Regex("^a=fmtp:(\\d+) apt=(\\d+).*$", RegexOption.IGNORE_CASE)

        val codecPayloads =
            lines.mapNotNull { line ->
                rtpMapRegex.matchEntire(line)?.groupValues?.get(1)
            }
        if (codecPayloads.isEmpty()) {
            return emptyList()
        }

        val rtxPayloads =
            lines.mapNotNull { line ->
                val match = fmtpRegex.matchEntire(line) ?: return@mapNotNull null
                val associatedPayload = match.groupValues[2]
                if (associatedPayload in codecPayloads) {
                    match.groupValues[1]
                } else {
                    null
                }
            }
        return (codecPayloads + rtxPayloads).distinct()
    }

    private fun reorderVideoMLine(
        mediaLine: String,
        preferredPayloads: List<String>,
    ): String {
        val parts = mediaLine.split(" ").filter { it.isNotEmpty() }
        if (parts.size <= 3) {
            return mediaLine
        }

        val header = parts.take(3)
        val payloads = parts.drop(3)
        val reorderedPayloads =
            preferredPayloads.filter { it in payloads } +
                payloads.filterNot { it in preferredPayloads }
        return (header + reorderedPayloads).joinToString(" ")
    }

    private const val LINE_SEPARATOR = "\r\n"
}
