package io.relavr.sender.app

import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.relavr.sender.core.model.UiText
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@ExperimentalCamera2Interop
internal fun senderQrScannerOverlay(
    scannerReady: Boolean,
    onDismiss: () -> Unit,
    onPayloadScanned: (String) -> Unit,
    onFailure: (UiText) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xD9040E16)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.84f),
            shape = RoundedCornerShape(32.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.sender_scanner_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.sender_scanner_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color(0xFF07111F),
                                                Color(0xFF0D2030),
                                            ),
                                    ),
                                shape = RoundedCornerShape(24.dp),
                            ).border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(24.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (scannerReady) {
                        senderQrScannerPreview(
                            onPayloadScanned = onPayloadScanned,
                            onFailure = onFailure,
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.sender_scanner_requesting_camera_permission),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.sender_scanner_manual_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sender_scanner_cancel))
                }
            }
        }
    }
}

@Composable
@ExperimentalCamera2Interop
private fun senderQrScannerPreview(
    onPayloadScanned: (String) -> Unit,
    onFailure: (UiText) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView =
        remember {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(context, lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val bound = AtomicBoolean(false)

        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview =
                        Preview
                            .Builder()
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis =
                        ImageAnalysis
                            .Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    analysis.setAnalyzer(
                        analyzerExecutor,
                        SenderQrCodeAnalyzer(
                            onPayloadScanned = onPayloadScanned,
                            onFailure = onFailure,
                        ),
                    )

                    val cameraSelector = QuestHeadsetCameraSelector.resolve(context, cameraProvider)
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
                    bound.set(true)
                }.onFailure {
                    onFailure(UiText.of(R.string.sender_scanner_camera_start_failed))
                }
            },
            mainExecutor,
        )

        onDispose {
            analyzerExecutor.shutdown()
            if (cameraProviderFuture.isDone && bound.get()) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }
}

private class SenderQrCodeAnalyzer(
    private val onPayloadScanned: (String) -> Unit,
    private val onFailure: (UiText) -> Unit,
) : ImageAnalysis.Analyzer {
    private val delivered = AtomicBoolean(false)
    private val reader = QRCodeReader()
    private val hints =
        EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.TRY_HARDER, true)
        }

    override fun analyze(image: ImageProxy) {
        try {
            if (delivered.get()) {
                return
            }
            val plane = image.planes.firstOrNull() ?: return
            plane.buffer.rewind()
            val bytes = ByteArray(plane.buffer.remaining())
            plane.buffer.get(bytes)
            val source =
                PlanarYUVLuminanceSource(
                    bytes,
                    plane.rowStride,
                    image.height,
                    0,
                    0,
                    image.width,
                    image.height,
                    false,
                )
            val result =
                reader.decode(
                    BinaryBitmap(HybridBinarizer(source)),
                    hints,
                )
            if (delivered.compareAndSet(false, true)) {
                onPayloadScanned(result.text)
            }
        } catch (_: NotFoundException) {
        } catch (_: ChecksumException) {
        } catch (_: FormatException) {
        } catch (throwable: Throwable) {
            if (delivered.compareAndSet(false, true)) {
                onFailure(UiText.of(R.string.sender_scanner_decode_failed))
            }
        } finally {
            reader.reset()
            image.close()
        }
    }
}
