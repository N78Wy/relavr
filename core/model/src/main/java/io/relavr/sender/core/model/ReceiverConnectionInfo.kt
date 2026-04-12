package io.relavr.sender.core.model

data class ReceiverConnectionInfo(
    val receiverName: String,
    val sessionId: String,
    val host: String,
    val port: Int,
    val authRequired: Boolean,
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
) {
    init {
        require(receiverName.isNotBlank()) { "Receiver name must not be blank." }
        require(sessionId.isNotBlank()) { "Session ID must not be blank." }
        require(host.isNotBlank()) { "Receiver host must not be blank." }
        require(port in 1..65535) { "Receiver port must be between 1 and 65535." }
    }

    val endpoint: String = formatHostAndPort(host, port)
    val webSocketUrl: String = "ws://${formatHostForUri(host)}:$port"

    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1
    }
}

private fun formatHostAndPort(
    host: String,
    port: Int,
): String = "${formatHostForUri(host)}:$port"

private fun formatHostForUri(host: String): String {
    val trimmedHost = host.trim()
    return if (':' in trimmedHost && !trimmedHost.startsWith('[') && !trimmedHost.endsWith(']')) {
        "[$trimmedHost]"
    } else {
        trimmedHost
    }
}
