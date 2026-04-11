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
        require(receiverName.isNotBlank()) { "接收端名称不能为空" }
        require(sessionId.isNotBlank()) { "Session ID 不能为空" }
        require(host.isNotBlank()) { "接收端主机地址不能为空" }
        require(port in 1..65535) { "接收端端口必须在 1 到 65535 之间" }
    }

    val endpoint: String = formatHostAndPort(host, port)
    val webSocketUrl: String = "ws://${formatHostForUri(host)}:$port"

    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1
    }
}
