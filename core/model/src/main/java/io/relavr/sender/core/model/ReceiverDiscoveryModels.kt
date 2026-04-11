package io.relavr.sender.core.model

data class DiscoveredReceiver(
    val serviceName: String,
    val receiverName: String,
    val sessionId: String,
    val host: String,
    val port: Int,
    val authRequired: Boolean,
    val protocolVersion: Int = ReceiverConnectionInfo.CURRENT_PROTOCOL_VERSION,
) {
    init {
        require(serviceName.isNotBlank()) { "服务实例名不能为空" }
        require(receiverName.isNotBlank()) { "接收端名称不能为空" }
        require(sessionId.isNotBlank()) { "Session ID 不能为空" }
        require(host.isNotBlank()) { "接收端主机地址不能为空" }
        require(port in 1..65535) { "接收端端口必须在 1 到 65535 之间" }
    }

    val endpoint: String = formatHostAndPort(host, port)
    val webSocketUrl: String = "ws://${formatHostForUri(host)}:$port"

    fun toConnectionInfo(): ReceiverConnectionInfo =
        ReceiverConnectionInfo(
            receiverName = receiverName,
            sessionId = sessionId,
            host = host,
            port = port,
            authRequired = authRequired,
            protocolVersion = protocolVersion,
        )
}

enum class ReceiverDiscoveryPhase {
    Idle,
    Discovering,
    Error,
}

data class ReceiverDiscoverySnapshot(
    val phase: ReceiverDiscoveryPhase = ReceiverDiscoveryPhase.Idle,
    val receivers: List<DiscoveredReceiver> = emptyList(),
    val errorMessage: String? = null,
)

internal fun formatHostAndPort(
    host: String,
    port: Int,
): String = "${formatHostForUri(host)}:$port"

internal fun formatHostForUri(host: String): String =
    if (host.contains(':') && !host.startsWith("[") && !host.endsWith("]")) {
        "[$host]"
    } else {
        host
    }
