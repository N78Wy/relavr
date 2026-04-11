package io.relavr.sender.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

internal object QuestHeadsetCameraSelector {
    @ExperimentalCamera2Interop
    fun resolve(
        context: Context,
        cameraProvider: ProcessCameraProvider,
    ): CameraSelector {
        val preferredCameraId =
            runCatching { findPreferredCameraId(context) }
                .getOrNull()

        if (preferredCameraId != null) {
            selectorForCameraId(cameraProvider, preferredCameraId)?.let { return it }
        }
        return fallbackSelector(cameraProvider)
    }

    internal fun pickPreferredPassthroughCameraId(cameraSources: Map<String, Int?>): String? =
        cameraSources.entries.firstOrNull { (_, source) -> source == PASSTHROUGH_RGB_SOURCE }?.key

    private fun findPreferredCameraId(context: Context): String? {
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return null
        val cameraSources =
            cameraManager.cameraIdList.associateWith { cameraId ->
                readCameraSource(cameraManager.getCameraCharacteristics(cameraId))
            }
        return pickPreferredPassthroughCameraId(cameraSources)
    }

    private fun readCameraSource(characteristics: CameraCharacteristics): Int? {
        val vendorKey =
            characteristics.keys.firstOrNull { key ->
                key.name == CAMERA_SOURCE_VENDOR_TAG || key.name.endsWith(".camera_source")
            } ?: return null

        @Suppress("UNCHECKED_CAST")
        return (characteristics.get(vendorKey as CameraCharacteristics.Key<Any>) as? Number)?.toInt()
    }

    @ExperimentalCamera2Interop
    private fun selectorForCameraId(
        cameraProvider: ProcessCameraProvider,
        cameraId: String,
    ): CameraSelector? {
        val selector =
            CameraSelector
                .Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { cameraInfo ->
                        runCatching { Camera2CameraInfo.from(cameraInfo).cameraId == cameraId }.getOrDefault(false)
                    }
                }.build()
        return runCatching {
            if (cameraProvider.hasCamera(selector)) {
                selector
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fallbackSelector(cameraProvider: ProcessCameraProvider): CameraSelector {
        val preferredSelectors = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
        preferredSelectors
            .firstOrNull { selector ->
                runCatching { cameraProvider.hasCamera(selector) }.getOrDefault(false)
            }?.let { return it }

        return CameraSelector
            .Builder()
            .addCameraFilter { cameraInfos -> cameraInfos.take(1) }
            .build()
    }

    private const val CAMERA_SOURCE_VENDOR_TAG = "com.meta.extra_metadata.camera_source"
    private const val PASSTHROUGH_RGB_SOURCE = 0
}
