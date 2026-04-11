package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.session.AudioCaptureSource
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class PlaybackAudioBufferBridge(
    private val onAudioDegraded: (String) -> Unit,
) : JavaAudioDeviceModule.AudioBufferCallback {
    private val sourceRef = AtomicReference<AudioCaptureSource?>(null)
    private val degradationReported = AtomicBoolean(false)
    private var silenceBuffer = ByteArray(0)

    fun attachSource(source: AudioCaptureSource?) {
        sourceRef.set(source)
        degradationReported.set(false)
    }

    override fun onBuffer(
        buffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        bytesRead: Int,
        captureTimeNs: Long,
    ): Long {
        val source = sourceRef.get() ?: return writeSilence(buffer, bytesRead, captureTimeNs)
        return runCatching {
            ensureSilenceBuffer(bytesRead)
            buffer.clear()
            buffer.put(silenceBuffer, 0, bytesRead)
            buffer.rewind()
            val result = source.read(buffer, bytesRead)
            if (result.bytesRead < 0 || result.bytesRead > bytesRead) {
                throw IllegalStateException("音频采样结果长度无效：${result.bytesRead}")
            }
            result.timestampNs
        }.getOrElse { throwable ->
            if (degradationReported.compareAndSet(false, true)) {
                onAudioDegraded(throwable.message ?: "音频已降级为静音/仅视频")
            }
            writeSilence(buffer, bytesRead, captureTimeNs)
        }
    }

    private fun writeSilence(
        buffer: ByteBuffer,
        bytesRead: Int,
        captureTimeNs: Long,
    ): Long {
        ensureSilenceBuffer(bytesRead)
        buffer.clear()
        buffer.put(silenceBuffer, 0, bytesRead)
        buffer.rewind()
        return if (captureTimeNs != 0L) {
            captureTimeNs
        } else {
            System.nanoTime()
        }
    }

    private fun ensureSilenceBuffer(bytesRead: Int) {
        if (silenceBuffer.size < bytesRead) {
            silenceBuffer = ByteArray(bytesRead)
        }
    }
}
