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
        ?: throw IllegalArgumentException("缺少字符串字段: $key")

fun Map<String, String>.requireJsonInt(key: String): Int =
    get(key)
        ?.toIntOrNull()
        ?: throw IllegalArgumentException("缺少整数字段: $key")

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

                else -> throw IllegalArgumentException("JSON 结构无效")
            }
        }

        throw IllegalArgumentException("JSON 对象缺少结束符")
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
        throw IllegalArgumentException("JSON 字符串缺少结束引号")
    }

    private fun readEscape(): Char {
        if (index >= source.length) {
            throw IllegalArgumentException("JSON 转义序列不完整")
        }
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicodeEscape()
            else -> throw IllegalArgumentException("不支持的 JSON 转义: \\$escaped")
        }
    }

    private fun readUnicodeEscape(): Char {
        if (index + 4 > source.length) {
            throw IllegalArgumentException("Unicode 转义长度不足")
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
            throw IllegalArgumentException("JSON 字段值为空")
        }
        return source.substring(start, index)
    }

    private fun expect(expected: Char) {
        if (peek() != expected) {
            throw IllegalArgumentException("JSON 结构无效，期望字符: $expected")
        }
        index += 1
    }

    private fun peek(): Char {
        if (index >= source.length) {
            throw IllegalArgumentException("JSON 内容意外结束")
        }
        return source[index]
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index += 1
        }
    }
}
