package io.relavr.sender.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.relavr.sender.app.ui.theme.relavrTheme
import io.relavr.sender.feature.streamcontrol.StreamControlViewModel
import io.relavr.sender.feature.streamcontrol.streamControlScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as RelavrApplication).appContainer

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    streamControlScreen(
                        uiState = uiState,
                        onSignalingEndpointChanged = viewModel::onSignalingEndpointChanged,
                        onSessionIdChanged = viewModel::onSessionIdChanged,
                        onCodecPreferenceChanged = viewModel::onCodecPreferenceChanged,
                        onAudioEnabledChanged = viewModel::onAudioEnabledChanged,
                        onResolutionChanged = viewModel::onResolutionChanged,
                        onFpsChanged = viewModel::onFpsChanged,
                        onBitrateChanged = viewModel::onBitrateChanged,
                        onStartClicked = viewModel::onStartClicked,
                        onStopClicked = viewModel::onStopClicked,
                    )
                }
            }
        }
    }
}
