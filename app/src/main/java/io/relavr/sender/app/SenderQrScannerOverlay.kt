package io.relavr.sender.app

import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@ExperimentalCamera2Interop
internal fun senderQrScannerOverlay(
    scannerReady: Boolean,
    onDismiss: () -> Unit,
    onPayloadScanned: (String) -> Unit,
    onFailure: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xCC02060B)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.8f),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "扫码连接接收端",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "扫描 receiver 控制台二维码后会自动填入地址并立即发起开播。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .background(Color(0xFF040B12), RoundedCornerShape(20.dp)),
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
                                text = "正在请求头显相机权限",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text(
                    text = "如果扫码失败，可以关闭弹层后继续手动填写 WebSocket 地址和 Session ID。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("取消扫码")
                }
            }
        }
    }
}

@Composable
@ExperimentalCamera2Interop
private fun senderQrScannerPreview(
    onPayloadScanned: (String) -> Unit,
    onFailure: (String) -> Unit,
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
                }.onFailure { throwable ->
                    onFailure(throwable.message ?: "头显相机启动失败")
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
    private val onFailure: (String) -> Unit,
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
                onFailure(throwable.message ?: "二维码识别失败")
            }
        } finally {
            reader.reset()
            image.close()
        }
    }
}
