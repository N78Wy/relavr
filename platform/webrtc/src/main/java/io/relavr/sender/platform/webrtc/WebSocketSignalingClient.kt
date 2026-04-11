package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.session.SenderException
import io.relavr.sender.core.session.SignalingClient
import io.relavr.sender.core.session.SignalingMessage
import io.relavr.sender.core.session.SignalingSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebSocketSignalingClient(
    private val logger: AppLogger,
    private val httpClient: OkHttpClient,
) : SignalingClient {
    constructor(
        logger: AppLogger,
    ) : this(
        logger = logger,
        httpClient = OkHttpClient(),
    )

    override suspend fun open(config: StreamConfig): SignalingSession {
        val session =
            WebSocketSignalingSession(
                expectedSessionId = config.trimmedSessionId,
                logger = logger,
            )
        val request =
            Request
                .Builder()
                .url(config.trimmedSignalingEndpoint)
                .build()
        val webSocket = httpClient.newWebSocket(request, session.listener())
        session.attach(webSocket)

        return try {
            withTimeout(OPEN_TIMEOUT_MS) {
                session.awaitOpen()
                session
            }
        } catch (throwable: Throwable) {
            session.cancel()
            throw when (throwable) {
                is SenderException -> throwable
                else ->
                    SenderException(
                        SenderError.SignalingFailed(
                            throwable.message ?: "信令连接建立失败",
                        ),
                    )
            }
        }
    }

    private companion object {
        const val OPEN_TIMEOUT_MS = 10_000L
    }
}

private class WebSocketSignalingSession(
    private val expectedSessionId: String,
    private val logger: AppLogger,
) : SignalingSession {
    private val incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 32)
    private val opened = CompletableDeferred<Unit>()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var closing: Boolean = false

    @Volatile
    private var closed: Boolean = false

    override val messages: Flow<SignalingMessage> = incomingMessages

    fun attach(webSocket: WebSocket) {
        this.webSocket = webSocket
    }

    suspend fun awaitOpen() {
        opened.await()
    }

    fun listener(): WebSocketListener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                opened.complete(Unit)
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                runCatching {
                    JsonSignalingMessageCodec.decode(text)
                }.onSuccess { message ->
                    if (message.sessionId == expectedSessionId) {
                        incomingMessages.tryEmit(message)
                    }
                }.onFailure { throwable ->
                    logger.error(
                        TAG,
                        "收到无法解析的信令消息: ${throwable.message}",
                        throwable,
                    )
                    incomingMessages.tryEmit(
                        SignalingMessage.Error(
                            sessionId = expectedSessionId,
                            message = "收到无法解析的信令消息",
                        ),
                    )
                }
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                if (!closing) {
                    webSocket.close(code, reason)
                }
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                closed = true
                if (!closing) {
                    incomingMessages.tryEmit(
                        SignalingMessage.Error(
                            sessionId = expectedSessionId,
                            message = reason.ifBlank { "信令连接已关闭" },
                        ),
                    )
                    if (!opened.isCompleted) {
                        opened.completeExceptionally(
                            SenderException(SenderError.SignalingFailed("信令连接已关闭")),
                        )
                    }
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                if (closing) {
                    return
                }

                val message = t.message ?: "WebSocket 信令连接失败"
                logger.error(TAG, message, t)
                incomingMessages.tryEmit(
                    SignalingMessage.Error(
                        sessionId = expectedSessionId,
                        message = message,
                    ),
                )
                if (!opened.isCompleted) {
                    opened.completeExceptionally(
                        SenderException(SenderError.SignalingFailed(message)),
                    )
                }
            }
        }

    override suspend fun send(message: SignalingMessage) {
        if (closed) {
            throw SenderException(SenderError.SignalingFailed("信令连接已关闭"))
        }
        val socket =
            webSocket
                ?: throw SenderException(SenderError.SignalingFailed("信令连接尚未建立"))

        val sent = socket.send(JsonSignalingMessageCodec.encode(message))
        if (!sent) {
            throw SenderException(SenderError.SignalingFailed("发送信令消息失败"))
        }
    }

    fun cancel() {
        closed = true
        webSocket?.cancel()
    }

    override fun close() {
        if (closed) {
            return
        }
        closing = true
        closed = true
        webSocket?.close(NORMAL_CLOSURE_CODE, "sender-close")
    }

    private companion object {
        const val TAG = "WebSocketSignaling"
        const val NORMAL_CLOSURE_CODE = 1000
    }
}
