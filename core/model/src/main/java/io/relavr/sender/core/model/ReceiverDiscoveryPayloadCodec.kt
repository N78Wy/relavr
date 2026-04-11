package io.relavr.sender.core.model

object ReceiverDiscoveryPayloadCodec {
    fun decode(
        serviceName: String,
        host: String,
        resolvedPort: Int,
        attributes: Map<String, String>,
    ): DiscoveredReceiver {
        val protocolVersion =
            attributes[VERSION_KEY]?.toIntOrNull()
                ?: throw IllegalArgumentException("发现协议版本缺失或无效")
        if (protocolVersion != ReceiverConnectionInfo.CURRENT_PROTOCOL_VERSION) {
            throw IllegalArgumentException("不支持的发现协议版本: $protocolVersion")
        }

        val authRequired =
            when (val auth = attributes[AUTH_KEY]?.trim()) {
                AUTH_PIN -> true
                AUTH_NONE -> false
                null, "" -> throw IllegalArgumentException("发现协议缺少鉴权模式")
                else -> throw IllegalArgumentException("未知的鉴权模式: $auth")
            }
        val port =
            attributes[PORT_KEY]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: resolvedPort
        if (port !in 1..65535) {
            throw IllegalArgumentException("发现协议端口无效: $port")
        }

        return DiscoveredReceiver(
            serviceName = serviceName,
            receiverName = attributes.requireValue(NAME_KEY),
            sessionId = attributes.requireValue(SESSION_ID_KEY),
            host = host,
            port = port,
            authRequired = authRequired,
            protocolVersion = protocolVersion,
        )
    }

    private fun Map<String, String>.requireValue(key: String): String =
        this[key]?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("发现协议缺少字段: $key")

    private const val NAME_KEY = "name"
    private const val VERSION_KEY = "ver"
    private const val PORT_KEY = "port"
    private const val SESSION_ID_KEY = "sessionId"
    private const val AUTH_KEY = "auth"
    private const val AUTH_PIN = "pin"
    private const val AUTH_NONE = "none"
}
