package io.relavr.sender.feature.streamcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.DiscoveredReceiver
import io.relavr.sender.core.model.ReceiverConnectPayloadCodec
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.session.ReceiverDiscoveryController
import io.relavr.sender.core.session.StreamingSessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StreamControlViewModel(
    private val sessionController: StreamingSessionController,
    private val discoveryController: ReceiverDiscoveryController,
    initialConfig: StreamConfig = StreamConfig(),
) : ViewModel() {
    private val config = MutableStateFlow(initialConfig)
    private val qrScannerState = MutableStateFlow(QrScannerState())
    private val screenActive = MutableStateFlow(false)
    private val pendingDiscoveryReceiver = MutableStateFlow<DiscoveredReceiver?>(null)

    val uiState: StateFlow<StreamControlUiState> =
        combine(
            config,
            qrScannerState,
            discoveryController.observeState(),
            pendingDiscoveryReceiver,
            sessionController.observeState(),
        ) { currentConfig, currentQrScannerState, discoveryState, pendingReceiver, sessionState ->
            buildStreamControlUiState(
                config = currentConfig,
                scannerState = currentQrScannerState,
                discoveryState = discoveryState,
                pendingReceiver = pendingReceiver,
                sessionSnapshot = sessionState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                buildStreamControlUiState(
                    config = initialConfig,
                    scannerState = qrScannerState.value,
                    discoveryState = discoveryController.observeState().value,
                    pendingReceiver = pendingDiscoveryReceiver.value,
                    sessionSnapshot = sessionController.observeState().value,
                ),
        )

    init {
        viewModelScope.launch {
            sessionController.refreshCapabilities()
        }
        viewModelScope.launch {
            sessionController
                .observeState()
                .map { snapshot -> snapshot.capabilities }
                .distinctUntilChanged()
                .collect { capabilities ->
                    if (capabilities == null || capabilities.supportedCodecs.isEmpty()) {
                        return@collect
                    }
                    config.update { current ->
                        if (capabilities.supports(current.codecPreference)) {
                            current
                        } else {
                            current.copy(codecPreference = capabilities.defaultCodec)
                        }
                    }
                }
        }
        viewModelScope.launch {
            var discoveryRunning = false
            combine(
                screenActive,
                sessionController.observeState().map(::isConfigEditable),
            ) { active, editable -> active to editable }
                .distinctUntilChanged()
                .collect { (active, editable) ->
                    val shouldDiscover = active && editable
                    if (shouldDiscover && !discoveryRunning) {
                        discoveryController.start()
                        discoveryRunning = true
                    } else if (!shouldDiscover && discoveryRunning) {
                        discoveryController.stop()
                        discoveryRunning = false
                    }
                }
        }
    }

    fun onSignalingEndpointChanged(endpoint: String) {
        config.update { current ->
            current.copy(signalingEndpoint = endpoint)
        }
    }

    fun onSessionIdChanged(sessionId: String) {
        config.update { current ->
            current.copy(sessionId = sessionId)
        }
    }

    fun onCodecPreferenceChanged(codecPreference: CodecPreference) {
        config.update { current ->
            current.copy(codecPreference = codecPreference)
        }
    }

    fun onAudioEnabledChanged(enabled: Boolean) {
        config.update { current ->
            current.copy(audioEnabled = enabled)
        }
    }

    fun onOpenScannerClicked() {
        qrScannerState.update { current ->
            current.copy(visible = true, errorMessage = null)
        }
    }

    fun onScannerDismissed() {
        qrScannerState.update { current ->
            current.copy(visible = false)
        }
    }

    fun onScannerFailed(message: String) {
        qrScannerState.update { current ->
            current.copy(visible = false, errorMessage = message)
        }
    }

    fun onScannerPayloadReceived(payload: String) {
        val connectionInfo =
            runCatching { ReceiverConnectPayloadCodec.decode(payload) }
                .getOrElse { throwable ->
                    onScannerFailed(throwable.message ?: "二维码解析失败")
                    return
                }

        val updatedConfig =
            config.value.copy(
                signalingEndpoint = connectionInfo.webSocketUrl,
                sessionId = connectionInfo.sessionId,
            )
        config.value = updatedConfig

        qrScannerState.value =
            QrScannerState(
                visible = false,
                lastReceiver = connectionInfo,
                errorMessage = null,
            )
        viewModelScope.launch {
            sessionController.start(updatedConfig)
        }
    }

    fun onDiscoveryRefreshClicked() {
        viewModelScope.launch {
            discoveryController.refresh()
        }
    }

    fun onDiscoveredReceiverClicked(receiver: DiscoveredReceiver) {
        pendingDiscoveryReceiver.value = receiver
    }

    fun onDiscoveryConnectionDismissed() {
        pendingDiscoveryReceiver.value = null
    }

    fun onDiscoveryConnectionConfirmed() {
        val receiver = pendingDiscoveryReceiver.value ?: return
        pendingDiscoveryReceiver.value = null
        val updatedConfig =
            config.value.copy(
                signalingEndpoint = receiver.webSocketUrl,
                sessionId = receiver.sessionId,
            )
        config.value = updatedConfig

        viewModelScope.launch {
            sessionController.start(updatedConfig)
        }
    }

    fun onScreenStarted() {
        screenActive.value = true
    }

    fun onScreenStopped() {
        screenActive.value = false
    }

    fun onResolutionChanged(resolution: VideoResolution) {
        config.update { current ->
            current.copy(resolution = resolution)
        }
    }

    fun onFpsChanged(fps: Int) {
        config.update { current ->
            current.copy(fps = fps)
        }
    }

    fun onBitrateChanged(bitrateKbps: Int) {
        config.update { current ->
            current.copy(bitrateKbps = bitrateKbps)
        }
    }

    fun onStartClicked() {
        viewModelScope.launch {
            sessionController.start(config.value)
        }
    }

    fun onStopClicked() {
        viewModelScope.launch {
            sessionController.stop()
        }
    }
}

class StreamControlViewModelFactory(
    private val sessionController: StreamingSessionController,
    private val discoveryController: ReceiverDiscoveryController,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StreamControlViewModel(
            sessionController = sessionController,
            discoveryController = discoveryController,
        ) as T
}

private fun isConfigEditable(snapshot: io.relavr.sender.core.model.StreamingSessionSnapshot): Boolean =
    !snapshot.isStreaming &&
        snapshot.captureState != io.relavr.sender.core.model.CaptureState.RequestingPermission &&
        snapshot.publishState != io.relavr.sender.core.model.PublishState.Preparing
