package io.relavr.sender.platform.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.ReceiverDiscoveryPayloadCodec
import io.relavr.sender.core.session.ReceiverDiscoveryEvent
import io.relavr.sender.core.session.ReceiverDiscoverySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class AndroidNsdReceiverDiscoverySource(
    context: Context,
    private val logger: AppLogger,
) : ReceiverDiscoverySource {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private val eventFlow = MutableSharedFlow<ReceiverDiscoveryEvent>(extraBufferCapacity = 32)
    private val lock = Any()
    private val resolveListeners = mutableMapOf<String, NsdManager.ResolveListener>()

    private var discoveryState: DiscoveryState = DiscoveryState.Idle

    override val events: Flow<ReceiverDiscoveryEvent> = eventFlow

    override suspend fun start() {
        acquireMulticastLock()
        while (true) {
            val waitForStop: CompletableDeferred<Unit>?
            val startRequest: StartRequest?
            synchronized(lock) {
                when (val current = discoveryState) {
                    DiscoveryState.Idle -> {
                        val started = CompletableDeferred<Unit>()
                        val listener = discoveryListener(started)
                        discoveryState =
                            DiscoveryState.Starting(
                                listener = listener,
                                started = started,
                            )
                        waitForStop = null
                        startRequest = StartRequest(listener = listener, started = started)
                    }

                    is DiscoveryState.Starting,
                    is DiscoveryState.Running,
                    -> return

                    is DiscoveryState.Stopping -> {
                        waitForStop = current.stopped
                        startRequest = null
                    }
                }
            }

            if (waitForStop != null) {
                waitForStop.await()
                continue
            }

            check(startRequest != null)
            runCatching {
                nsdManager.discoverServices(
                    RECEIVER_DISCOVERY_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    startRequest.listener,
                )
            }.onFailure { throwable ->
                synchronized(lock) {
                    val current = discoveryState
                    if (current is DiscoveryState.Starting && current.listener === startRequest.listener) {
                        discoveryState = DiscoveryState.Idle
                    }
                }
                startRequest.started.completeExceptionally(
                    IllegalStateException(
                        throwable.message ?: "启动局域网发现失败",
                        throwable,
                    ),
                )
            }

            startRequest.started.await()
            return
        }
    }

    override suspend fun stop() {
        while (true) {
            val waitForStop: CompletableDeferred<Unit>?
            val listenerToStop: NsdManager.DiscoveryListener?
            synchronized(lock) {
                when (val current = discoveryState) {
                    DiscoveryState.Idle -> return

                    is DiscoveryState.Starting -> {
                        val stopped = current.stopped ?: CompletableDeferred<Unit>()
                        discoveryState =
                            current.copy(
                                stopRequested = true,
                                stopped = stopped,
                            )
                        waitForStop = stopped
                        listenerToStop = null
                    }

                    is DiscoveryState.Running -> {
                        clearResolveListenersLocked()
                        val stopped = CompletableDeferred<Unit>()
                        discoveryState = DiscoveryState.Stopping(listener = current.listener, stopped = stopped)
                        waitForStop = stopped
                        listenerToStop = current.listener
                    }

                    is DiscoveryState.Stopping -> {
                        waitForStop = current.stopped
                        listenerToStop = null
                    }
                }
            }

            if (listenerToStop != null) {
                runCatching {
                    nsdManager.stopServiceDiscovery(listenerToStop)
                }.onFailure { throwable ->
                    synchronized(lock) {
                        val current = discoveryState
                        if (current is DiscoveryState.Stopping && current.listener === listenerToStop) {
                            discoveryState = DiscoveryState.Idle
                            clearResolveListenersLocked()
                            current.stopped.complete(Unit)
                        }
                    }
                    logger.error(TAG, "停止局域网发现失败", throwable)
                }
            }

            waitForStop?.await()
            releaseMulticastLock()
            return
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("relavr-sender-mdns")
        }
        multicastLock?.let { lock ->
            if (!lock.isHeld) {
                lock.acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
    }

    private fun discoveryListener(started: CompletableDeferred<Unit>): NsdManager.DiscoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) {
                val stopped: CompletableDeferred<Unit>?
                synchronized(lock) {
                    val current = discoveryState
                    if (current !is DiscoveryState.Starting || current.listener !== this) {
                        return
                    }
                    discoveryState = DiscoveryState.Idle
                    clearResolveListenersLocked()
                    stopped = current.stopped
                }

                started.completeExceptionally(
                    IllegalStateException("启动局域网发现失败：${describeDiscoveryError(errorCode)}"),
                )
                stopped?.complete(Unit)
            }

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) {
                val stopped: CompletableDeferred<Unit>?
                synchronized(lock) {
                    val current = discoveryState
                    if (current !is DiscoveryState.Stopping || current.listener !== this) {
                        return
                    }
                    discoveryState = DiscoveryState.Idle
                    clearResolveListenersLocked()
                    stopped = current.stopped
                }

                logger.error(TAG, "停止局域网发现失败：${describeDiscoveryError(errorCode)}", null)
                stopped?.complete(Unit)
            }

            override fun onDiscoveryStarted(serviceType: String) {
                val stopAfterStart: Boolean
                val stopDeferred: CompletableDeferred<Unit>?
                synchronized(lock) {
                    val current = discoveryState
                    if (current !is DiscoveryState.Starting || current.listener !== this) {
                        return
                    }
                    stopAfterStart = current.stopRequested
                    stopDeferred = current.stopped
                    discoveryState =
                        if (stopAfterStart) {
                            DiscoveryState.Stopping(
                                listener = this,
                                stopped = stopDeferred ?: CompletableDeferred(),
                            )
                        } else {
                            DiscoveryState.Running(listener = this)
                        }
                }

                started.complete(Unit)
                if (stopAfterStart) {
                    clearResolveListeners()
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                        .onFailure { throwable ->
                            synchronized(lock) {
                                val current = discoveryState
                                if (current is DiscoveryState.Stopping && current.listener === this) {
                                    discoveryState = DiscoveryState.Idle
                                    clearResolveListenersLocked()
                                    current.stopped.complete(Unit)
                                }
                            }
                            logger.error(TAG, "启动后立即停止局域网发现失败", throwable)
                        }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                val stopped: CompletableDeferred<Unit>?
                synchronized(lock) {
                    val current = discoveryState
                    if (current !is DiscoveryState.Stopping || current.listener !== this) {
                        return
                    }
                    discoveryState = DiscoveryState.Idle
                    clearResolveListenersLocked()
                    stopped = current.stopped
                }
                stopped?.complete(Unit)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == RECEIVER_DISCOVERY_SERVICE_TYPE) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                eventFlow.tryEmit(
                    ReceiverDiscoveryEvent.Lost(
                        serviceName = serviceInfo.serviceName.orEmpty(),
                    ),
                )
            }
        }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName ?: return
        synchronized(lock) {
            if (resolveListeners.containsKey(serviceName)) {
                return
            }
        }

        val listener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    removeResolveListener(serviceName)
                    eventFlow.tryEmit(
                        ReceiverDiscoveryEvent.Failure(
                            message = "解析接收端 $serviceName 失败：${describeResolveError(errorCode)}",
                        ),
                    )
                }

                override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                    removeResolveListener(serviceName)

                    val host =
                        resolvedServiceInfo.host
                            ?.hostAddress
                            ?.substringBefore('%')
                            ?.takeIf { it.isNotBlank() }

                    if (host == null) {
                        eventFlow.tryEmit(
                            ReceiverDiscoveryEvent.Failure(
                                message = "接收端 $serviceName 未返回可用地址",
                            ),
                        )
                        return
                    }

                    val attributes = resolvedServiceInfo.attributes.toUtf8Map()
                    runCatching {
                        ReceiverDiscoveryPayloadCodec.decode(
                            serviceName = serviceName,
                            host = host,
                            resolvedPort = resolvedServiceInfo.port,
                            attributes = attributes,
                        )
                    }.onSuccess { receiver ->
                        eventFlow.tryEmit(ReceiverDiscoveryEvent.Found(receiver))
                    }.onFailure { throwable ->
                        logger.error(TAG, "解析接收端广播字段失败: $serviceName", throwable)
                        eventFlow.tryEmit(
                            ReceiverDiscoveryEvent.Failure(
                                message = throwable.message ?: "解析接收端广播字段失败",
                            ),
                        )
                    }
                }
            }

        synchronized(lock) {
            resolveListeners[serviceName] = listener
        }
        nsdManager.resolveService(serviceInfo, listener)
    }

    private fun clearResolveListeners() {
        synchronized(lock) {
            clearResolveListenersLocked()
        }
    }

    private fun clearResolveListenersLocked() {
        resolveListeners.clear()
    }

    private fun removeResolveListener(serviceName: String) {
        synchronized(lock) {
            resolveListeners.remove(serviceName)
        }
    }

    private sealed interface DiscoveryState {
        data object Idle : DiscoveryState

        data class Starting(
            val listener: NsdManager.DiscoveryListener,
            val started: CompletableDeferred<Unit>,
            val stopRequested: Boolean = false,
            val stopped: CompletableDeferred<Unit>? = null,
        ) : DiscoveryState

        data class Running(
            val listener: NsdManager.DiscoveryListener,
        ) : DiscoveryState

        data class Stopping(
            val listener: NsdManager.DiscoveryListener,
            val stopped: CompletableDeferred<Unit>,
        ) : DiscoveryState
    }

    private data class StartRequest(
        val listener: NsdManager.DiscoveryListener,
        val started: CompletableDeferred<Unit>,
    )

    private companion object {
        const val TAG = "AndroidNsdDiscovery"
    }
}

internal const val RECEIVER_DISCOVERY_SERVICE_TYPE = "_relavr-recv._tcp"

internal fun Map<String, ByteArray>.toUtf8Map(): Map<String, String> =
    entries.associate { (key, value) -> key to value.toString(Charsets.UTF_8) }

internal fun describeDiscoveryError(errorCode: Int): String =
    when (errorCode) {
        NsdManager.FAILURE_INTERNAL_ERROR -> "系统内部错误（0）"
        NsdManager.FAILURE_ALREADY_ACTIVE -> "已有同类型 discovery 正在运行（3）"
        NsdManager.FAILURE_MAX_LIMIT -> "已达到系统允许的 discovery 上限（4）"
        NsdManager.FAILURE_OPERATION_NOT_RUNNING -> "discovery 当前未运行（5）"
        NsdManager.FAILURE_BAD_PARAMETERS -> "discovery 参数无效（6）"
        FAILURE_PERMISSION_DENIED -> "缺少本地网络相关权限（7）"
        else -> "未知错误码（$errorCode）"
    }

internal fun describeResolveError(errorCode: Int): String =
    when (errorCode) {
        NsdManager.FAILURE_INTERNAL_ERROR -> "系统内部错误（0）"
        NsdManager.FAILURE_ALREADY_ACTIVE -> "已有同名 resolve 请求在运行（3）"
        NsdManager.FAILURE_MAX_LIMIT -> "已达到系统允许的 resolve 上限（4）"
        NsdManager.FAILURE_OPERATION_NOT_RUNNING -> "resolve 当前未运行（5）"
        NsdManager.FAILURE_BAD_PARAMETERS -> "resolve 参数无效（6）"
        FAILURE_PERMISSION_DENIED -> "缺少本地网络相关权限（7）"
        else -> "未知错误码（$errorCode）"
    }

private const val FAILURE_PERMISSION_DENIED = 7
