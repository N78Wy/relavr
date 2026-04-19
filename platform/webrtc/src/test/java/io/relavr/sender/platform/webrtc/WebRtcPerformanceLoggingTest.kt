package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.model.VideoStreamProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcPerformanceLoggingTest {
    @Test
    fun `audio input snapshot reports pressure when short reads occur`() {
        val snapshot =
            AudioInputPerformanceSnapshot(
                captureFormat = null,
                callbackCountSinceLastSnapshot = 25,
                capturedBytesSinceLastSnapshot = 48_000,
                shortReadCallbacksSinceLastSnapshot = 1,
                maxCallbackGapMs = 12,
                started = true,
                lastError = null,
            )

        assertTrue(snapshot.hasPressure)
    }

    @Test
    fun `audio input snapshot reports no pressure for healthy callbacks`() {
        val snapshot =
            AudioInputPerformanceSnapshot(
                captureFormat = null,
                callbackCountSinceLastSnapshot = 25,
                capturedBytesSinceLastSnapshot = 48_000,
                shortReadCallbacksSinceLastSnapshot = 0,
                maxCallbackGapMs = 12,
                started = true,
                lastError = null,
            )

        assertFalse(snapshot.hasPressure)
    }

    @Test
    fun `performance log message includes the new audio diagnostics`() {
        val message =
            buildPerformanceLogMessage(
                assessment =
                    VideoEncoderAssessment(
                        encodedFps = 29.4,
                        reportedFps = 30.0,
                        qualityLimitationReason = "none",
                        overloaded = false,
                        reasonSummary = "encodedFps=29.40",
                    ),
                activeProfile =
                    VideoStreamProfile(
                        codecPreference = CodecPreference.H264,
                        resolution = VideoResolution(width = 1280, height = 720),
                        fps = 30,
                        bitrateKbps = 4000,
                    ),
                audioSnapshot =
                    AudioInputPerformanceSnapshot(
                        captureFormat = null,
                        callbackCountSinceLastSnapshot = 25,
                        capturedBytesSinceLastSnapshot = 48_000,
                        shortReadCallbacksSinceLastSnapshot = 0,
                        maxCallbackGapMs = 11,
                        started = true,
                        lastError = null,
                    ),
                accumulatedAudioCapturedBytes = 48_000,
                accumulatedAudioCallbackCount = 25,
                accumulatedAudioShortReads = 0,
                maxAudioCallbackGapMs = 11,
                memorySnapshot =
                    ProcessMemorySnapshot(
                        javaHeapUsedBytes = 8L * 1024L * 1024L,
                        javaHeapMaxBytes = 256L * 1024L * 1024L,
                        nativeHeapAllocatedBytes = 128L * 1024L * 1024L,
                        nativeHeapFreeBytes = 32L * 1024L * 1024L,
                        totalPssKb = 512 * 1024,
                    ),
            )

        assertTrue(message.contains("audioReadMs="))
        assertTrue(message.contains("audioCallbacks="))
        assertTrue(message.contains("audioShortReads="))
        assertTrue(message.contains("audioMaxGapMs="))
    }
}
