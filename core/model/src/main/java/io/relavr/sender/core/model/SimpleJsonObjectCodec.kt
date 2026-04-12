package io.relavr.sender.core.model

object SimpleJsonObjectCodec {
    fun encode(fields: Map<String, Any>): String =
        fields.entries.joinToString(
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
                    is Boolean -> append(value)
                    else -> {
                        append('"')
                        append(escape(value.toString()))
                        append('"')
                    }
                }
            }
        }

    fun decode(payload: String): Map<String, String> = FlatJsonParser(payload).parseObject()

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
}

fun Map<String, String>.requireJsonString(key: String): String =
    get(key)
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing required string field: $key")

fun Map<String, String>.requireJsonInt(key: String): Int =
    get(key)
        ?.toIntOrNull()
        ?: throw IllegalArgumentException("Missing required integer field: $key")

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

        throw IllegalArgumentException("JSON object is missing a closing brace")
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
        throw IllegalArgumentException("JSON string is missing a closing quote")
    }

    private fun readEscape(): Char {
        if (index >= source.length) {
            throw IllegalArgumentException("Incomplete JSON escape sequence")
        }
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicodeEscape()
            else -> throw IllegalArgumentException("Unsupported JSON escape sequence: \\$escaped")
        }
    }

    private fun readUnicodeEscape(): Char {
        if (index + 4 > source.length) {
            throw IllegalArgumentException("Unicode escape sequence is too short")
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
            throw IllegalArgumentException("JSON field value is empty")
        }
        return source.substring(start, index)
    }

    private fun expect(expected: Char) {
        if (peek() != expected) {
            throw IllegalArgumentException("Invalid JSON structure, expected: $expected")
        }
        index += 1
    }

    private fun peek(): Char {
        if (index >= source.length) {
            throw IllegalArgumentException("Unexpected end of JSON input")
        }
        return source[index]
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index += 1
        }
    }
}
