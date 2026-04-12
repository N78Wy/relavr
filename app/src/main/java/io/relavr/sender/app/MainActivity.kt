package io.relavr.sender.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
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

    private var headsetCameraGranted by mutableStateOf(false)

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
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            appContainer.recordAudioPermissionGateway.onPermissionResult(granted)
        }
    private val headsetCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            headsetCameraGranted = granted
            if (!granted) {
                viewModel.onScannerFailed(
                    UiText.of(io.relavr.sender.feature.streamcontrol.R.string.stream_control_scan_camera_permission_denied),
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        headsetCameraGranted = hasPermission(HEADSET_CAMERA_PERMISSION)

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appContainer.projectionPermissionGateway.permissionRequests.collect { intent ->
                    projectionPermissionLauncher.launch(intent)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appContainer.recordAudioPermissionGateway.permissionRequests.collect { permission ->
                    recordAudioPermissionLauncher.launch(permission)
                }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            relavrTheme {
                Surface(color = Color.Transparent) {
                    LaunchedEffect(uiState.scannerVisible, headsetCameraGranted) {
                        if (uiState.scannerVisible && !headsetCameraGranted) {
                            headsetCameraPermissionLauncher.launch(HEADSET_CAMERA_PERMISSION)
                        }
                    }
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
                        onSessionIdChanged = viewModel::onSessionIdChanged,
                        onCodecPreferenceChanged = viewModel::onCodecPreferenceChanged,
                        onAudioEnabledChanged = viewModel::onAudioEnabledChanged,
                        onOpenScannerClicked = viewModel::onOpenScannerClicked,
                        onResolutionChanged = viewModel::onResolutionChanged,
                        onFpsChanged = viewModel::onFpsChanged,
                        onBitrateChanged = viewModel::onBitrateChanged,
                        onStartClicked = viewModel::onStartClicked,
                        onStopClicked = viewModel::onStopClicked,
                    )
                    if (uiState.scannerVisible) {
                        senderQrScannerOverlay(
                            scannerReady = headsetCameraGranted,
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
        headsetCameraGranted = hasPermission(HEADSET_CAMERA_PERMISSION)
    }

    override fun onStop() {
        super.onStop()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val HEADSET_CAMERA_PERMISSION = "horizonos.permission.HEADSET_CAMERA"
    }
}
