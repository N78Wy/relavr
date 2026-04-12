package io.relavr.sender.feature.streamcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.ReceiverConnectPayloadCodec
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoResolution
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
    initialConfig: StreamConfig = StreamConfig(),
) : ViewModel() {
    private val config = MutableStateFlow(initialConfig)
    private val qrScannerState = MutableStateFlow(QrScannerState())

    val uiState: StateFlow<StreamControlUiState> =
        combine(
            config,
            qrScannerState,
            sessionController.observeState(),
        ) { currentConfig, currentQrScannerState, sessionState ->
            buildStreamControlUiState(
                config = currentConfig,
                scannerState = currentQrScannerState,
                sessionSnapshot = sessionState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                buildStreamControlUiState(
                    config = initialConfig,
                    scannerState = qrScannerState.value,
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

    fun onScannerFailed(message: UiText) {
        qrScannerState.update { current ->
            current.copy(visible = false, errorMessage = message)
        }
    }

    fun onScannerPayloadReceived(payload: String) {
        val connectionInfo =
            runCatching { ReceiverConnectPayloadCodec.decode(payload) }
                .getOrElse {
                    onScannerFailed(UiText.of(R.string.stream_control_scan_parse_failed))
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StreamControlViewModel(
            sessionController = sessionController,
        ) as T
}
