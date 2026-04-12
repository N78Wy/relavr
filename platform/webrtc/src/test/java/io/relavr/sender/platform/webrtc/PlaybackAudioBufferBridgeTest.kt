package io.relavr.sender.platform.webrtc

import android.media.projection.MediaProjection
import io.relavr.sender.core.session.AudioCaptureSource
import io.relavr.sender.core.session.AudioFrameReadResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class PlaybackAudioBufferBridgeTest {
    @Test
    fun `onBuffer writes audio source data into the callback buffer`() {
        val source =
            FakeAudioCaptureSource().also {
                it.bytes = byteArrayOf(1, 2, 3, 4)
                it.timestampNs = 456L
            }
        val bridge = PlaybackAudioBufferBridge(onAudioDegraded = {})
        bridge.attachSource(source)
        val buffer = ByteBuffer.allocateDirect(4)

        val timestampNs =
            bridge.onBuffer(
                buffer = buffer,
                audioFormat = 2,
                channelCount = 2,
                sampleRate = 48_000,
                bytesRead = 4,
                captureTimeNs = 0L,
            )

        val actual = ByteArray(4)
        buffer.rewind()
        buffer.get(actual)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), actual)
        assertEquals(456L, timestampNs)
    }

    @Test
    fun `audio read failures report degradation once and then fall back to silence`() {
        var degradationCount = 0
        val source =
            FakeAudioCaptureSource().also {
                it.shouldThrow = true
            }
        val bridge =
            PlaybackAudioBufferBridge(
                onAudioDegraded = { degradationCount += 1 },
            )
        bridge.attachSource(source)

        repeat(2) {
            val buffer = ByteBuffer.allocateDirect(4)
            bridge.onBuffer(
                buffer = buffer,
                audioFormat = 2,
                channelCount = 2,
                sampleRate = 48_000,
                bytesRead = 4,
                captureTimeNs = 123L,
            )

            val actual = ByteArray(4)
            buffer.rewind()
            buffer.get(actual)
            assertArrayEquals(byteArrayOf(0, 0, 0, 0), actual)
        }

        assertEquals(1, degradationCount)
    }
}

private class FakeAudioCaptureSource : AudioCaptureSource {
    override val sampleRateHz: Int = 48_000
    override val channelCount: Int = 2

    var bytes: ByteArray = byteArrayOf()
    var timestampNs: Long = 0L
    var shouldThrow: Boolean = false

    override fun start(mediaProjection: MediaProjection) = Unit

    override fun read(
        targetBuffer: ByteBuffer,
        requestedBytes: Int,
    ): AudioFrameReadResult {
        if (shouldThrow) {
            throw IllegalStateException("fake-read-failure")
        }
        val bytesToWrite = bytes.copyOf(minOf(requestedBytes, bytes.size))
        targetBuffer.put(bytesToWrite)
        return AudioFrameReadResult(
            bytesRead = bytesToWrite.size,
            timestampNs = timestampNs,
        )
    }

    override fun close() = Unit
}
