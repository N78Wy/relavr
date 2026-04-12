package io.relavr.sender.core.model

object ReceiverConnectPayloadCodec {
    fun encode(info: ReceiverConnectionInfo): String =
        SimpleJsonObjectCodec.encode(
            linkedMapOf(
                TYPE_KEY to TYPE_VALUE,
                VERSION_KEY to info.protocolVersion,
                NAME_KEY to info.receiverName,
                SESSION_ID_KEY to info.sessionId,
                SCHEME_KEY to info.scheme,
                HOST_KEY to info.host,
                PORT_KEY to info.port,
                PATH_KEY to info.path,
                AUTH_KEY to if (info.authRequired) AUTH_PIN else AUTH_NONE,
            ),
        )

    fun decode(payload: String): ReceiverConnectionInfo {
        val fields = SimpleJsonObjectCodec.decode(payload)
        val type = fields.requireJsonString(TYPE_KEY)
        if (type != TYPE_VALUE) {
            throw IllegalArgumentException("Unknown receiver-connect payload type: $type")
        }

        val protocolVersion = fields.requireJsonInt(VERSION_KEY)
        if (protocolVersion != ReceiverConnectionInfo.CURRENT_PROTOCOL_VERSION) {
            throw IllegalArgumentException("Unsupported receiver-connect protocol version: $protocolVersion")
        }

        val authRequired =
            when (val auth = fields.requireJsonString(AUTH_KEY)) {
                AUTH_PIN -> true
                AUTH_NONE -> false
                else -> throw IllegalArgumentException("Unknown authentication mode: $auth")
            }

        return ReceiverConnectionInfo(
            receiverName = fields.requireJsonString(NAME_KEY),
            sessionId = fields.requireJsonString(SESSION_ID_KEY),
            scheme = fields.requireJsonString(SCHEME_KEY),
            host = fields.requireJsonString(HOST_KEY),
            port = fields.requireJsonInt(PORT_KEY),
            path = fields.requireJsonString(PATH_KEY),
            authRequired = authRequired,
            protocolVersion = protocolVersion,
        )
    }

    private const val TYPE_KEY = "type"
    private const val TYPE_VALUE = "receiver-connect"
    private const val VERSION_KEY = "ver"
    private const val NAME_KEY = "name"
    private const val SESSION_ID_KEY = "sessionId"
    private const val SCHEME_KEY = "scheme"
    private const val HOST_KEY = "host"
    private const val PORT_KEY = "port"
    private const val PATH_KEY = "path"
    private const val AUTH_KEY = "auth"
    private const val AUTH_PIN = "pin"
    private const val AUTH_NONE = "none"
}
