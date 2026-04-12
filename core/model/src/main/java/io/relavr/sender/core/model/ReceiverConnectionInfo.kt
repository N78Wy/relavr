package io.relavr.sender.core.model

data class ReceiverConnectionInfo(
    val receiverName: String,
    val sessionId: String,
    val host: String,
    val port: Int,
    val authRequired: Boolean,
    val scheme: String = DEFAULT_WEB_SOCKET_SCHEME,
    val path: String = DEFAULT_WEB_SOCKET_PATH,
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
) {
    init {
        require(receiverName.isNotBlank()) { "Receiver name must not be blank." }
        require(sessionId.isNotBlank()) { "Session ID must not be blank." }
        require(host.isNotBlank()) { "Receiver host must not be blank." }
        require(port in 1..65535) { "Receiver port must be between 1 and 65535." }
        require(scheme in SUPPORTED_WEB_SOCKET_SCHEMES) { "Receiver scheme must be ws or wss." }
        require(isValidPath(path)) { "Receiver path must start with / and must not contain query or fragment." }
    }

    val endpoint: String = formatHostAndPort(host, port)
    val webSocketUrl: String = buildWebSocketUrl(scheme, host, port, path)

    companion object {
        const val CURRENT_PROTOCOL_VERSION = 2
        const val DEFAULT_WEB_SOCKET_SCHEME = "ws"
        const val DEFAULT_WEB_SOCKET_PATH = "/"

        private val SUPPORTED_WEB_SOCKET_SCHEMES = setOf("ws", "wss")
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

private fun buildWebSocketUrl(
    scheme: String,
    host: String,
    port: Int,
    path: String,
): String {
    val authority = "$scheme://${formatHostForUri(host)}:$port"
    return if (path == ReceiverConnectionInfo.DEFAULT_WEB_SOCKET_PATH) {
        authority
    } else {
        authority + path
    }
}

private fun isValidPath(path: String): Boolean = path.isNotBlank() && path.startsWith('/') && '?' !in path && '#' !in path
