package io.relavr.sender.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.relavr.sender.app.ui.theme.relavrTheme
import io.relavr.sender.core.model.UiText
import io.relavr.sender.feature.streamcontrol.StreamControlViewModel
import io.relavr.sender.feature.streamcontrol.streamControlScreen
import kotlinx.coroutines.launch

@ExperimentalCamera2Interop
class MainActivity : AppCompatActivity() {
    private val appContainer: AppContainer
        get() = (application as RelavrApplication).appContainer

    private var headsetCameraPermissionRequestPending by mutableStateOf(false)

    private val viewModel: StreamControlViewModel by viewModels {
        appContainer.streamControlViewModelFactory
    }

    private val projectionPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            appContainer.projectionPermissionGateway.onPermissionResult(
                resultCode = result.resultCode,
                data = result.data,
            )
        }
    private val headsetCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            appContainer.headsetCameraPermissionGateway.onPermissionResult(
                granted = granted,
                shouldShowRationale = shouldShowRequestPermissionRationale(HEADSET_CAMERA_PERMISSION),
            )
        }
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            appContainer.recordAudioPermissionGateway.onPermissionResult(
                granted = granted,
                shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        syncHeadsetCameraPermission()
        syncRecordAudioPermission()

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appContainer.projectionPermissionGateway.permissionRequests.collect { intent ->
                    projectionPermissionLauncher.launch(intent)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appContainer.headsetCameraPermissionGateway.permissionRequestFlow.collect {
                    headsetCameraPermissionLauncher.launch(HEADSET_CAMERA_PERMISSION)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appContainer.headsetCameraPermissionGateway.appSettingsRequestFlow.collect {
                    openAppSettings()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appContainer.recordAudioPermissionGateway.permissionRequestFlow.collect {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appContainer.recordAudioPermissionGateway.appSettingsRequestFlow.collect {
                    openAppSettings()
                }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val headsetCameraPermissionState by appContainer.headsetCameraPermissionGateway.observeState().collectAsStateWithLifecycle()
            relavrTheme {
                Surface(color = Color.Transparent) {
                    streamControlScreen(
                        uiState = uiState,
                        selectedLanguageTag = currentAppLanguage().tag,
                        onLanguageTagSelected = { tag ->
                            applyAppLanguage(
                                if (tag == AppLanguage.SimplifiedChinese.tag) {
                                    AppLanguage.SimplifiedChinese
                                } else {
                                    AppLanguage.English
                                },
                            )
                        },
                        onSignalingEndpointChanged = viewModel::onSignalingEndpointChanged,
                        onSignalingSchemeChanged = viewModel::onSignalingSchemeChanged,
                        onSignalingHostChanged = viewModel::onSignalingHostChanged,
                        onSignalingPortChanged = viewModel::onSignalingPortChanged,
                        onSignalingPathChanged = viewModel::onSignalingPathChanged,
                        onSessionIdChanged = viewModel::onSessionIdChanged,
                        onCodecPreferenceChanged = viewModel::onCodecPreferenceChanged,
                        onAudioEnabledChanged = ::handleAudioEnabledChanged,
                        onOpenAudioPermissionSettingsClicked = viewModel::onOpenAudioPermissionSettingsClicked,
                        onOpenScannerClicked = ::handleOpenScannerClicked,
                        onResolutionChanged = viewModel::onResolutionChanged,
                        onFpsChanged = viewModel::onFpsChanged,
                        onBitrateChanged = viewModel::onBitrateChanged,
                        onStartClicked = ::handleStartClicked,
                        onStopClicked = viewModel::onStopClicked,
                    )
                    if (uiState.scannerVisible) {
                        senderQrScannerOverlay(
                            permissionState = headsetCameraPermissionState,
                            permissionRequestPending = headsetCameraPermissionRequestPending,
                            onOpenSettingsClicked = appContainer.headsetCameraPermissionGateway::openAppSettings,
                            onDismiss = viewModel::onScannerDismissed,
                            onPayloadScanned = viewModel::onScannerPayloadReceived,
                            onFailure = viewModel::onScannerFailed,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncHeadsetCameraPermission()
        syncRecordAudioPermission()
    }

    private fun handleOpenScannerClicked() {
        syncHeadsetCameraPermission()
        viewModel.onOpenScannerClicked()
        if (appContainer.headsetCameraPermissionGateway.observeState().value == HeadsetCameraPermissionState.Requestable) {
            requestHeadsetCameraPermission()
        }
    }

    private fun requestHeadsetCameraPermission() {
        lifecycleScope.launch {
            headsetCameraPermissionRequestPending = true
            try {
                when (appContainer.headsetCameraPermissionGateway.requestPermissionIfNeeded()) {
                    HeadsetCameraPermissionState.Granted,
                    HeadsetCameraPermissionState.PermanentlyDenied,
                    -> Unit

                    HeadsetCameraPermissionState.Requestable ->
                        viewModel.onScannerFailed(
                            UiText.of(io.relavr.sender.feature.streamcontrol.R.string.stream_control_scan_camera_permission_denied),
                        )
                }
            } finally {
                headsetCameraPermissionRequestPending = false
            }
        }
    }

    private fun handleAudioEnabledChanged(enabled: Boolean) {
        syncRecordAudioPermission()
        viewModel.onAudioEnabledChanged(enabled)
    }

    private fun handleStartClicked() {
        syncRecordAudioPermission()
        viewModel.onStartClicked()
    }

    private fun syncHeadsetCameraPermission() {
        appContainer.headsetCameraPermissionGateway.syncPermissionStatus(
            shouldShowRationale = shouldShowRequestPermissionRationale(HEADSET_CAMERA_PERMISSION),
        )
    }

    private fun syncRecordAudioPermission() {
        appContainer.recordAudioPermissionGateway.syncPermissionStatus(
            shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    companion object {
        private const val HEADSET_CAMERA_PERMISSION = "horizonos.permission.HEADSET_CAMERA"
    }
}
