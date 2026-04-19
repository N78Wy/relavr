package io.relavr.sender.feature.streamcontrol

import java.net.URI

data class SignalingEndpointDraft(
    val scheme: String = DEFAULT_SCHEME,
    val host: String = "",
    val port: String = DEFAULT_PORT,
    val path: String = DEFAULT_PATH,
) {
    fun toPersistedEndpoint(): String {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) {
            return ""
        }

        val trimmedScheme = scheme.normalizedScheme()
        val trimmedPort = port.trim()
        val normalizedPath = path.normalizedPath()
        val portSegment = trimmedPort.takeIf { it.isNotEmpty() }?.let { ":$it" }.orEmpty()
        return buildString {
            append(trimmedScheme)
            append("://")
            append(trimmedHost)
            append(portSegment)
            append(normalizedPath)
        }
    }

    fun isReadyToConnect(): Boolean {
        val parsedPort = port.trim().toIntOrNull() ?: return false
        return host.trim().isNotEmpty() && parsedPort in 1..65535
    }

    companion object {
        const val DEFAULT_SCHEME = "ws"
        const val SECURE_SCHEME = "wss"
        const val DEFAULT_PATH = "/ws"
        const val DEFAULT_PORT = "8080"
        const val DEFAULT_SECURE_PORT = "443"
    }
}

internal fun parseSignalingEndpointDraft(endpoint: String): SignalingEndpointDraft {
    val trimmedEndpoint = endpoint.trim()
    if (trimmedEndpoint.isEmpty()) {
        return SignalingEndpointDraft()
    }

    val parsedUri = runCatching { URI(trimmedEndpoint) }.getOrNull()
    if (parsedUri == null) {
        return SignalingEndpointDraft(host = trimmedEndpoint)
    }

    val scheme = parsedUri.scheme.normalizedScheme()
    val host = parsedUri.host.orEmpty().ifEmpty { parsedUri.authority.orEmpty().substringBefore(':') }
    val port =
        when {
            parsedUri.port != -1 -> parsedUri.port.toString()
            scheme == SignalingEndpointDraft.SECURE_SCHEME -> SignalingEndpointDraft.DEFAULT_SECURE_PORT
            else -> SignalingEndpointDraft.DEFAULT_PORT
        }
    val path = parsedUri.rawPath.normalizedPath()
    return SignalingEndpointDraft(
        scheme = scheme,
        host = host,
        port = port,
        path = path,
    )
}

internal fun String.normalizedScheme(): String =
    when (trim().lowercase()) {
        SignalingEndpointDraft.SECURE_SCHEME -> SignalingEndpointDraft.SECURE_SCHEME
        else -> SignalingEndpointDraft.DEFAULT_SCHEME
    }

internal fun String.normalizedPath(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return ""
    }
    return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
}
