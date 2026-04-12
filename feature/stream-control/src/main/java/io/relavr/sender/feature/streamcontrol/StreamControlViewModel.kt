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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    initialConfig: StreamConfig = StreamConfig(),
) : ViewModel() {
    private val config = MutableStateFlow(initialConfig)
    private val qrScannerState = MutableStateFlow(QrScannerState())
    private val audioPermissionRequestPending = MutableStateFlow(false)
    private val recordAudioPermissionStatus = MutableStateFlow<RecordAudioPermissionStatus?>(null)
    private val _recordAudioPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var hasLocalEdits = false
    private var hasLoadedPersistedConfig = false
    private var shouldEnableAudioAfterPermission = false

    val recordAudioPermissionRequests: SharedFlow<Unit> = _recordAudioPermissionRequests.asSharedFlow()

    val uiState: StateFlow<StreamControlUiState> =
        combine(
            config,
            qrScannerState,
            sessionController.observeState(),
            audioPermissionRequestPending,
            recordAudioPermissionStatus,
        ) { currentConfig, currentQrScannerState, sessionState, isAudioPermissionRequestPending, currentRecordAudioPermissionStatus ->
            buildStreamControlUiState(
                config = currentConfig,
                scannerState = currentQrScannerState,
                sessionSnapshot = sessionState,
                audioPermissionRequestPending = isAudioPermissionRequestPending,
                recordAudioPermissionStatus = currentRecordAudioPermissionStatus,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                buildStreamControlUiState(
                    config = initialConfig,
                    scannerState = qrScannerState.value,
                    sessionSnapshot = sessionController.observeState().value,
                    audioPermissionRequestPending = audioPermissionRequestPending.value,
                    recordAudioPermissionStatus = recordAudioPermissionStatus.value,
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
            hasLoadedPersistedConfig = true
            maybeRequestRecordAudioPermissionForEnabledAudio()
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

    fun onAudioToggleRequested(enabled: Boolean) {
        if (!enabled) {
            shouldEnableAudioAfterPermission = false
            audioPermissionRequestPending.value = false
            updateConfig { current ->
                current.copy(audioEnabled = false)
            }
            return
        }

        if (recordAudioPermissionStatus.value == RecordAudioPermissionStatus.Granted) {
            updateConfig { current ->
                current.copy(audioEnabled = true)
            }
            return
        }

        if (recordAudioPermissionStatus.value == RecordAudioPermissionStatus.PermanentlyDenied) {
            shouldEnableAudioAfterPermission = false
            updateConfig { current ->
                current.copy(audioEnabled = false)
            }
            return
        }

        shouldEnableAudioAfterPermission = true
        requestRecordAudioPermission()
    }

    fun onRecordAudioPermissionSnapshot(status: RecordAudioPermissionStatus) {
        recordAudioPermissionStatus.value = status
        audioPermissionRequestPending.value = false
        if (status == RecordAudioPermissionStatus.PermanentlyDenied && config.value.audioEnabled) {
            shouldEnableAudioAfterPermission = false
            updateConfig { current ->
                current.copy(audioEnabled = false)
            }
        }
        maybeRequestRecordAudioPermissionForEnabledAudio()
    }

    fun onRecordAudioPermissionResolved(status: RecordAudioPermissionStatus) {
        recordAudioPermissionStatus.value = status
        audioPermissionRequestPending.value = false
        if (status == RecordAudioPermissionStatus.Granted) {
            if (shouldEnableAudioAfterPermission) {
                updateConfig { current ->
                    current.copy(audioEnabled = true)
                }
            }
        } else {
            updateConfig { current ->
                current.copy(audioEnabled = false)
            }
        }
        shouldEnableAudioAfterPermission = false
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
            sessionController.start(updatedConfig)
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
            sessionController.start(config.value)
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

    private fun maybeRequestRecordAudioPermissionForEnabledAudio() {
        if (!hasLoadedPersistedConfig || config.value.audioEnabled.not()) {
            return
        }
        if (recordAudioPermissionStatus.value != RecordAudioPermissionStatus.Requestable) {
            return
        }
        shouldEnableAudioAfterPermission = true
        requestRecordAudioPermission()
    }

    private fun requestRecordAudioPermission() {
        if (audioPermissionRequestPending.value) {
            return
        }
        audioPermissionRequestPending.value = true
        viewModelScope.launch {
            _recordAudioPermissionRequests.emit(Unit)
        }
    }
}

class StreamControlViewModelFactory(
    private val sessionController: StreamingSessionController,
    private val configStore: StreamControlConfigStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StreamControlViewModel(
            sessionController = sessionController,
            configStore = configStore,
        ) as T
}
