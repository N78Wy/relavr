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
    private val signalingEndpointDraft = MutableStateFlow(parseSignalingEndpointDraft(initialConfig.signalingEndpoint))
    private val qrScannerState = MutableStateFlow(QrScannerState())
    private val audioPermissionRequestPending = MutableStateFlow(false)
    private var hasLocalEdits = false

    val uiState: StateFlow<StreamControlUiState> =
        combine(
            combine(config, signalingEndpointDraft) { currentConfig, currentDraft ->
                currentConfig to currentDraft
            },
            qrScannerState,
            recordAudioPermissionController.observeState(),
            audioPermissionRequestPending,
            sessionController.observeState(),
        ) { configAndDraft, currentQrScannerState, permissionState, permissionPending, sessionState ->
            val (currentConfig, currentDraft) = configAndDraft
            buildStreamControlUiState(
                config = currentConfig,
                signalingEndpointDraft = currentDraft,
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
                    signalingEndpointDraft = signalingEndpointDraft.value,
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
                signalingEndpointDraft.value = parseSignalingEndpointDraft(persistedConfig.signalingEndpoint)
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
        updateConfig(syncEndpointDraft = true) { current ->
            current.copy(signalingEndpoint = endpoint)
        }
    }

    fun onSignalingSchemeChanged(scheme: String) {
        updateEndpointDraft { current ->
            val normalizedScheme = scheme.normalizedScheme()
            val updatedPort =
                when (current.port.trim()) {
                    "",
                    SignalingEndpointDraft.DEFAULT_PORT,
                    SignalingEndpointDraft.DEFAULT_SECURE_PORT,
                    ->
                        if (normalizedScheme == SignalingEndpointDraft.SECURE_SCHEME) {
                            SignalingEndpointDraft.DEFAULT_SECURE_PORT
                        } else {
                            SignalingEndpointDraft.DEFAULT_PORT
                        }
                    else -> current.port
                }
            current.copy(
                scheme = normalizedScheme,
                port = updatedPort,
            )
        }
    }

    fun onSignalingHostChanged(host: String) {
        updateEndpointDraft { current ->
            current.copy(host = host)
        }
    }

    fun onSignalingPortChanged(port: String) {
        updateEndpointDraft { current ->
            current.copy(port = port.filter(Char::isDigit))
        }
    }

    fun onSignalingPathChanged(path: String) {
        updateEndpointDraft { current ->
            current.copy(path = path)
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
        updateConfig(
            updatedConfig = updatedConfig,
            syncEndpointDraft = true,
        )

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
        syncEndpointDraft: Boolean = false,
    ) {
        val updatedDraft =
            if (syncEndpointDraft) {
                parseSignalingEndpointDraft(updatedConfig.signalingEndpoint)
            } else {
                signalingEndpointDraft.value
            }
        if (updatedConfig == config.value && updatedDraft == signalingEndpointDraft.value) {
            return
        }
        if (markAsLocalEdit) {
            hasLocalEdits = true
        }
        config.value = updatedConfig
        if (syncEndpointDraft) {
            signalingEndpointDraft.value = updatedDraft
        }
        if (persist) {
            persistConfig(updatedConfig)
        }
    }

    private fun updateConfig(
        markAsLocalEdit: Boolean = true,
        persist: Boolean = true,
        syncEndpointDraft: Boolean = false,
        transform: (StreamConfig) -> StreamConfig,
    ) {
        val currentConfig = config.value
        updateConfig(
            updatedConfig = transform(currentConfig),
            markAsLocalEdit = markAsLocalEdit,
            persist = persist,
            syncEndpointDraft = syncEndpointDraft,
        )
    }

    private fun updateEndpointDraft(transform: (SignalingEndpointDraft) -> SignalingEndpointDraft) {
        val updatedDraft = transform(signalingEndpointDraft.value)
        if (updatedDraft == signalingEndpointDraft.value) {
            return
        }
        signalingEndpointDraft.value = updatedDraft
        updateConfig(
            updatedConfig =
                config.value.copy(
                    signalingEndpoint = updatedDraft.toPersistedEndpoint(),
                ),
            syncEndpointDraft = false,
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
