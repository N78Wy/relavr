package io.relavr.sender.feature.streamcontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.ReceiverConnectPayloadCodec
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.session.RecordAudioPermissionController
import io.relavr.sender.core.session.RecordAudioPermissionState
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
    private val configStore: StreamControlConfigStore,
    private val recordAudioPermissionController: RecordAudioPermissionController,
    initialConfig: StreamConfig = StreamConfig(),
) : ViewModel() {
    private val config = MutableStateFlow(initialConfig)
    private val qrScannerState = MutableStateFlow(QrScannerState())
    private val audioPermissionRequestPending = MutableStateFlow(false)
    private var hasLocalEdits = false

    val uiState: StateFlow<StreamControlUiState> =
        combine(
            config,
            qrScannerState,
            recordAudioPermissionController.observeState(),
            audioPermissionRequestPending,
            sessionController.observeState(),
        ) { currentConfig, currentQrScannerState, permissionState, permissionPending, sessionState ->
            buildStreamControlUiState(
                config = currentConfig,
                scannerState = currentQrScannerState,
                recordAudioPermissionState = permissionState,
                audioPermissionRequestPending = permissionPending,
                sessionSnapshot = sessionState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                buildStreamControlUiState(
                    config = initialConfig,
                    scannerState = qrScannerState.value,
                    recordAudioPermissionState = recordAudioPermissionController.observeState().value,
                    audioPermissionRequestPending = audioPermissionRequestPending.value,
                    sessionSnapshot = sessionController.observeState().value,
                ),
        )

    init {
        viewModelScope.launch {
            val persistedConfig =
                runCatching { configStore.load() }
                    .getOrDefault(initialConfig)
            if (!hasLocalEdits) {
                config.value = persistedConfig
            }
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
                    val currentConfig = config.value
                    if (capabilities.supports(currentConfig.codecPreference)) {
                        return@collect
                    }
                    updateConfig(
                        markAsLocalEdit = false,
                        transform = { current ->
                            current.copy(codecPreference = capabilities.defaultCodec)
                        },
                    )
                }
        }
    }

    fun onSignalingEndpointChanged(endpoint: String) {
        updateConfig { current ->
            current.copy(signalingEndpoint = endpoint)
        }
    }

    fun onSessionIdChanged(sessionId: String) {
        updateConfig { current ->
            current.copy(sessionId = sessionId)
        }
    }

    fun onCodecPreferenceChanged(codecPreference: CodecPreference) {
        updateConfig { current ->
            current.copy(codecPreference = codecPreference)
        }
    }

    fun onAudioEnabledChanged(enabled: Boolean) {
        if (!enabled) {
            updateConfig { current ->
                current.copy(audioEnabled = false)
            }
            return
        }

        viewModelScope.launch {
            val permissionState = requestRecordAudioPermission()
            updateConfig(
                markAsLocalEdit = true,
                transform = { current ->
                    current.copy(audioEnabled = permissionState == RecordAudioPermissionState.Granted)
                },
            )
        }
    }

    fun onOpenAudioPermissionSettingsClicked() {
        recordAudioPermissionController.openAppSettings()
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
        updateConfig(updatedConfig)

        qrScannerState.value =
            QrScannerState(
                visible = false,
                lastReceiver = connectionInfo,
                errorMessage = null,
            )
        viewModelScope.launch {
            sessionController.start(prepareConfigForStart(updatedConfig))
        }
    }

    fun onResolutionChanged(resolution: VideoResolution) {
        updateConfig { current ->
            current.copy(resolution = resolution)
        }
    }

    fun onFpsChanged(fps: Int) {
        updateConfig { current ->
            current.copy(fps = fps)
        }
    }

    fun onBitrateChanged(bitrateKbps: Int) {
        updateConfig { current ->
            current.copy(bitrateKbps = bitrateKbps)
        }
    }

    fun onStartClicked() {
        viewModelScope.launch {
            sessionController.start(prepareConfigForStart(config.value))
        }
    }

    fun onStopClicked() {
        viewModelScope.launch {
            sessionController.stop()
        }
    }

    private fun updateConfig(
        updatedConfig: StreamConfig,
        markAsLocalEdit: Boolean = true,
        persist: Boolean = true,
    ) {
        if (updatedConfig == config.value) {
            return
        }
        if (markAsLocalEdit) {
            hasLocalEdits = true
        }
        config.value = updatedConfig
        if (persist) {
            persistConfig(updatedConfig)
        }
    }

    private fun updateConfig(
        markAsLocalEdit: Boolean = true,
        persist: Boolean = true,
        transform: (StreamConfig) -> StreamConfig,
    ) {
        val currentConfig = config.value
        updateConfig(
            updatedConfig = transform(currentConfig),
            markAsLocalEdit = markAsLocalEdit,
            persist = persist,
        )
    }

    private fun persistConfig(updatedConfig: StreamConfig) {
        viewModelScope.launch {
            runCatching { configStore.save(updatedConfig) }
        }
    }

    private suspend fun prepareConfigForStart(currentConfig: StreamConfig): StreamConfig {
        if (!currentConfig.audioEnabled) {
            return currentConfig
        }

        val permissionState = requestRecordAudioPermission()
        if (permissionState == RecordAudioPermissionState.Granted) {
            return currentConfig
        }

        val fallbackConfig = currentConfig.copy(audioEnabled = false)
        updateConfig(
            updatedConfig = fallbackConfig,
            markAsLocalEdit = true,
        )
        return fallbackConfig
    }

    private suspend fun requestRecordAudioPermission(): RecordAudioPermissionState {
        audioPermissionRequestPending.value = true
        return try {
            recordAudioPermissionController.requestPermissionIfNeeded()
        } finally {
            audioPermissionRequestPending.value = false
        }
    }
}

class StreamControlViewModelFactory(
    private val sessionController: StreamingSessionController,
    private val configStore: StreamControlConfigStore,
    private val recordAudioPermissionController: RecordAudioPermissionController,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StreamControlViewModel(
            sessionController = sessionController,
            configStore = configStore,
            recordAudioPermissionController = recordAudioPermissionController,
        ) as T
}
