package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.session.SignalingMessage

internal object JsonSignalingMessageCodec {
    fun encode(message: SignalingMessage): String {
        val fields =
            linkedMapOf<String, Any>(
                "type" to message.typeName(),
                "sessionId" to message.sessionId,
            )

        when (message) {
            is SignalingMessage.Join -> {
                fields["role"] = message.role
            }

            is SignalingMessage.Offer -> {
                fields["sdp"] = message.sdp
            }

            is SignalingMessage.Answer -> {
                fields["sdp"] = message.sdp
            }

            is SignalingMessage.IceCandidate -> {
                fields["candidate"] = message.candidate
                fields["sdpMid"] = message.sdpMid
                fields["sdpMLineIndex"] = message.sdpMLineIndex
            }

            is SignalingMessage.Leave -> Unit

            is SignalingMessage.Error -> {
                message.code?.let { fields["code"] = it }
                message.message?.let { fields["message"] = it }
            }
        }

        return fields.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
        ) { (key, value) ->
            buildString {
                append('"')
                append(escape(key))
                append('"')
                append(':')
                when (value) {
                    is Number -> append(value)
                    else -> {
                        append('"')
                        append(escape(value.toString()))
                        append('"')
                    }
                }
            }
        }
    }

    fun decode(payload: String): SignalingMessage {
        val fields = FlatJsonParser(payload).parseObject()
        val type = fields.requireString("type")
        val sessionId = fields.requireString("sessionId")

        return when (type) {
            TYPE_JOIN ->
                SignalingMessage.Join(
                    sessionId = sessionId,
                    role = fields["role"] ?: "sender",
                )

            TYPE_OFFER ->
                SignalingMessage.Offer(
                    sessionId = sessionId,
                    sdp = fields.requireString("sdp"),
                )

            TYPE_ANSWER ->
                SignalingMessage.Answer(
                    sessionId = sessionId,
                    sdp = fields.requireString("sdp"),
                )

            TYPE_ICE_CANDIDATE ->
                SignalingMessage.IceCandidate(
                    sessionId = sessionId,
                    candidate = fields.requireString("candidate"),
                    sdpMid = fields.requireString("sdpMid"),
                    sdpMLineIndex = fields.requireInt("sdpMLineIndex"),
                )

            TYPE_LEAVE ->
                SignalingMessage.Leave(sessionId = sessionId)

            TYPE_ERROR ->
                SignalingMessage.Error(
                    sessionId = sessionId,
                    code = fields["code"],
                    message = fields["message"],
                )

            else -> throw IllegalArgumentException("Unknown signaling message type: $type")
        }
    }

    private fun SignalingMessage.typeName(): String =
        when (this) {
            is SignalingMessage.Join -> TYPE_JOIN
            is SignalingMessage.Offer -> TYPE_OFFER
            is SignalingMessage.Answer -> TYPE_ANSWER
            is SignalingMessage.IceCandidate -> TYPE_ICE_CANDIDATE
            is SignalingMessage.Leave -> TYPE_LEAVE
            is SignalingMessage.Error -> TYPE_ERROR
        }

    private fun escape(input: String): String =
        buildString(input.length) {
            input.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private fun Map<String, String>.requireString(key: String): String =
        get(key)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing string field: $key")

    private fun Map<String, String>.requireInt(key: String): Int =
        get(key)
            ?.toIntOrNull()
            ?: throw IllegalArgumentException("Missing integer field: $key")

    private const val TYPE_JOIN = "join"
    private const val TYPE_OFFER = "offer"
    private const val TYPE_ANSWER = "answer"
    private const val TYPE_ICE_CANDIDATE = "ice-candidate"
    private const val TYPE_LEAVE = "leave"
    private const val TYPE_ERROR = "error"
}

private class FlatJsonParser(
    payload: String,
) {
    private val source = payload.trim()
    private var index = 0

    fun parseObject(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        skipWhitespace()
        expect('{')
        skipWhitespace()

        if (peek() == '}') {
            index += 1
            return result
        }

        while (index < source.length) {
            val key = readQuotedString()
            skipWhitespace()
            expect(':')
            skipWhitespace()

            val value =
                when (peek()) {
                    '"' -> readQuotedString()
                    else -> readPrimitive()
                }
            result[key] = value

            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index += 1
                    skipWhitespace()
                }

                '}' -> {
                    index += 1
                    return result
                }

                else -> throw IllegalArgumentException("Invalid JSON structure")
            }
        }

        throw IllegalArgumentException("The JSON object is missing a closing delimiter.")
    }

    private fun readQuotedString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < source.length) {
            val char = source[index++]
            when (char) {
                '"' -> return builder.toString()
                '\\' -> builder.append(readEscape())
                else -> builder.append(char)
            }
        }
        throw IllegalArgumentException("The JSON string is missing a closing quote.")
    }

    private fun readEscape(): Char {
        if (index >= source.length) {
            throw IllegalArgumentException("The JSON escape sequence is incomplete.")
        }
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicodeEscape()
            else -> throw IllegalArgumentException("Unsupported JSON escape: \\$escaped")
        }
    }

    private fun readUnicodeEscape(): Char {
        if (index + 4 > source.length) {
            throw IllegalArgumentException("The Unicode escape sequence is too short.")
        }
        val hex = source.substring(index, index + 4)
        index += 4
        return hex.toInt(16).toChar()
    }

    private fun readPrimitive(): String {
        val start = index
        while (index < source.length && source[index] !in charArrayOf(',', '}', ' ', '\n', '\r', '\t')) {
            index += 1
        }
        if (start == index) {
            throw IllegalArgumentException("The JSON field value is empty.")
        }
        return source.substring(start, index)
    }

    private fun expect(expected: Char) {
        if (peek() != expected) {
            throw IllegalArgumentException("Invalid JSON structure. Expected character: $expected")
        }
        index += 1
    }

    private fun peek(): Char {
        if (index >= source.length) {
            throw IllegalArgumentException("Unexpected end of JSON content.")
        }
        return source[index]
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index += 1
        }
    }
}
