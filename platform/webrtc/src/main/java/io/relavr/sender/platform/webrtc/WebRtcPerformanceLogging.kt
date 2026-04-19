package io.relavr.sender.platform.webrtc

import android.os.Debug
import io.relavr.sender.core.model.VideoStreamProfile

internal fun shouldLogPerformanceSnapshot(
    pollsSinceLastPerformanceLog: Int,
    assessment: VideoEncoderAssessment,
    decision: AdaptiveVideoProfileDecision?,
    audioSnapshot: AudioInputPerformanceSnapshot?,
): Boolean =
    pollsSinceLastPerformanceLog >= PERFORMANCE_LOG_WINDOWS ||
        assessment.overloaded ||
        decision != null ||
        audioSnapshot?.hasPressure == true

internal fun buildPerformanceLogMessage(
    assessment: VideoEncoderAssessment,
    activeProfile: VideoStreamProfile,
    audioSnapshot: AudioInputPerformanceSnapshot?,
    accumulatedAudioCapturedBytes: Long,
    accumulatedAudioCallbackCount: Int,
    accumulatedAudioShortReads: Int,
    maxAudioCallbackGapMs: Long,
    memorySnapshot: ProcessMemorySnapshot,
): String =
    buildString {
        append("perf")
        append(" activeProfile=")
        append(activeProfile.summaryLabel)
        append(" overloaded=")
        append(assessment.overloaded)
        append(" encodedFps=")
        append(String.format("%.2f", assessment.encodedFps))
        append(" reportedFps=")
        append(assessment.reportedFps?.let { value -> String.format("%.2f", value) } ?: "n/a")
        append(" qualityLimitationReason=")
        append(assessment.qualityLimitationReason ?: "n/a")
        append(" audioReadMs=")
        append(accumulatedAudioCapturedBytes.toAudioMilliseconds())
        append(" audioCallbacks=")
        append(accumulatedAudioCallbackCount)
        append(" audioShortReads=")
        append(accumulatedAudioShortReads)
        append(" audioMaxGapMs=")
        append(maxAudioCallbackGapMs)
        append(" audioStarted=")
        append(audioSnapshot?.started ?: false)
        append(" audioLastError=")
        append(audioSnapshot?.lastError ?: "none")
        append(" audioFormat=")
        append(
            audioSnapshot?.captureFormat?.let { format ->
                "${format.sampleRateHz}/${format.channelCount}ch"
            } ?: "disabled",
        )
        append(" javaHeapMb=")
        append(memorySnapshot.javaHeapUsedBytes.toMegabytes())
        append("/")
        append(memorySnapshot.javaHeapMaxBytes.toMegabytes())
        append(" nativeHeapMb=")
        append(memorySnapshot.nativeHeapAllocatedBytes.toMegabytes())
        append(" nativeHeapFreeMb=")
        append(memorySnapshot.nativeHeapFreeBytes.toMegabytes())
        append(" totalPssMb=")
        append(memorySnapshot.totalPssKb / 1024)
        append(" assessment=")
        append(assessment.reasonSummary)
    }

internal fun captureProcessMemorySnapshot(): ProcessMemorySnapshot {
    val runtime = Runtime.getRuntime()
    val memoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memoryInfo)
    return ProcessMemorySnapshot(
        javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        javaHeapMaxBytes = runtime.maxMemory(),
        nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
        nativeHeapFreeBytes = Debug.getNativeHeapFreeSize(),
        totalPssKb = memoryInfo.totalPss,
    )
}

private fun Long.toAudioMilliseconds(): Long = ((toDouble() / 1920.0) * 10.0).toLong()

private fun Long.toMegabytes(): Long = this / (1024L * 1024L)

private const val PERFORMANCE_LOG_WINDOWS = 4
