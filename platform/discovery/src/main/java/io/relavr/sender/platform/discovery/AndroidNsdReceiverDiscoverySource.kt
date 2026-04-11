package io.relavr.sender.platform.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.ReceiverDiscoveryPayloadCodec
import io.relavr.sender.core.session.ReceiverDiscoveryEvent
import io.relavr.sender.core.session.ReceiverDiscoverySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidNsdReceiverDiscoverySource(
    context: Context,
    private val logger: AppLogger,
) : ReceiverDiscoverySource {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val eventFlow = MutableSharedFlow<ReceiverDiscoveryEvent>(extraBufferCapacity = 32)
    private val resolveListeners = mutableMapOf<String, NsdManager.ResolveListener>()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override val events: Flow<ReceiverDiscoveryEvent> = eventFlow

    override suspend fun start() {
        stop()

        suspendCancellableCoroutine { continuation ->
            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        discoveryListener = null
                        runCatching { nsdManager.stopServiceDiscovery(this) }
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("启动局域网发现失败，错误码: $errorCode"),
                            )
                        }
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        logger.error(TAG, "停止局域网发现失败，错误码: $errorCode", null)
                    }

                    override fun onDiscoveryStarted(serviceType: String) {
                        discoveryListener = this
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        if (discoveryListener === this) {
                            discoveryListener = null
                        }
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.serviceType == SERVICE_TYPE) {
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

            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            continuation.invokeOnCancellation {
                runCatching { nsdManager.stopServiceDiscovery(listener) }
                if (discoveryListener === listener) {
                    discoveryListener = null
                }
            }
        }
    }

    override suspend fun stop() {
        resolveListeners.clear()

        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsdManager.stopServiceDiscovery(listener) }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName ?: return
        if (resolveListeners.containsKey(serviceName)) {
            return
        }

        val listener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    resolveListeners.remove(serviceName)
                    eventFlow.tryEmit(
                        ReceiverDiscoveryEvent.Failure(
                            message = "解析接收端 $serviceName 失败，错误码: $errorCode",
                        ),
                    )
                }

                override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                    resolveListeners.remove(serviceName)

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
                            port = resolvedServiceInfo.port,
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

        resolveListeners[serviceName] = listener
        nsdManager.resolveService(serviceInfo, listener)
    }

    private companion object {
        const val TAG = "AndroidNsdDiscovery"
        const val SERVICE_TYPE = "_relavr-recv._tcp.local"
    }
}

internal fun Map<String, ByteArray>.toUtf8Map(): Map<String, String> =
    entries.associate { (key, value) -> key to value.toString(Charsets.UTF_8) }
