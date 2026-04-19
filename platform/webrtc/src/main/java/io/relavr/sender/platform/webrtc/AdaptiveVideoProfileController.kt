package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.model.VideoStreamProfile
import kotlin.math.max

internal data class VideoEncoderStatsSample(
    val timestampUs: Long,
    val framesEncoded: Long? = null,
    val framesPerSecond: Double? = null,
    val qualityLimitationReason: String? = null,
)

internal data class VideoEncoderAssessment(
    val encodedFps: Double,
    val reportedFps: Double?,
    val qualityLimitationReason: String?,
    val overloaded: Boolean,
    val reasonSummary: String,
)

internal sealed interface AdaptiveVideoProfileDecision {
    data class Downgrade(
        val profile: VideoStreamProfile,
        val assessment: VideoEncoderAssessment,
    ) : AdaptiveVideoProfileDecision

    data class Exhausted(
        val assessment: VideoEncoderAssessment,
    ) : AdaptiveVideoProfileDecision
}

internal class AdaptiveVideoProfileController(
    initialProfile: VideoStreamProfile,
    supportedProfiles: Set<VideoStreamProfile>,
) {
    private val allowedProfiles = supportedProfiles
    private var activeProfile = initialProfile
    private var previousSample: VideoEncoderStatsSample? = null
    private var latestAssessment: VideoEncoderAssessment? = null
    private var cooldownWindowsRemaining = 0
    private var overloadWindows = 0

    fun activeProfile(): VideoStreamProfile = activeProfile

    fun latestAssessment(): VideoEncoderAssessment? = latestAssessment

    fun evaluate(sample: VideoEncoderStatsSample): AdaptiveVideoProfileDecision? {
        val previous = previousSample
        previousSample = sample
        if (previous == null) {
            latestAssessment = null
            return null
        }

        val assessment = assess(sample, previous, activeProfile)
        latestAssessment = assessment

        if (!assessment.overloaded) {
            overloadWindows = 0
            cooldownWindowsRemaining = max(0, cooldownWindowsRemaining - 1)
            return null
        }

        if (cooldownWindowsRemaining > 0) {
            cooldownWindowsRemaining -= 1
            overloadWindows = 0
            return null
        }

        overloadWindows += 1
        val requiredWindows =
            if (nextLowerProfile(activeProfile) == null) MINIMUM_PROFILE_OVERLOAD_WINDOWS else OVERLOAD_WINDOWS_BEFORE_DOWNGRADE
        if (overloadWindows < requiredWindows) {
            return null
        }

        overloadWindows = 0
        val nextProfile = nextLowerProfile(activeProfile)
        if (nextProfile == null) {
            return AdaptiveVideoProfileDecision.Exhausted(assessment)
        }

        activeProfile = nextProfile
        cooldownWindowsRemaining = COOLDOWN_WINDOWS
        return AdaptiveVideoProfileDecision.Downgrade(
            profile = nextProfile,
            assessment = assessment,
        )
    }

    private fun nextLowerProfile(currentProfile: VideoStreamProfile): VideoStreamProfile? {
        val bitrateCandidates =
            StreamConfig.BITRATE_OPTIONS_KBPS
                .filter { bitrateKbps -> bitrateKbps < currentProfile.bitrateKbps }
                .sortedDescending()
                .map { bitrateKbps -> currentProfile.copy(bitrateKbps = bitrateKbps) }
        bitrateCandidates.firstOrNull(::isProfileAllowed)?.let { return it }

        val fpsCandidates =
            StreamConfig.FPS_OPTIONS
                .filter { option -> option < currentProfile.fps }
                .sortedDescending()
                .map { fps -> currentProfile.copy(fps = fps) }
        fpsCandidates.firstOrNull(::isProfileAllowed)?.let { return it }

        val resolutionCandidates =
            StreamConfig.RESOLUTION_OPTIONS
                .filter { resolution -> resolution.pixelCount < currentProfile.resolution.pixelCount }
                .sortedByDescending { resolution -> resolution.pixelCount }
                .map { resolution -> currentProfile.copy(resolution = resolution) }
        return resolutionCandidates.firstOrNull(::isProfileAllowed)
    }

    private fun isProfileAllowed(profile: VideoStreamProfile): Boolean = allowedProfiles.isEmpty() || profile in allowedProfiles

    private companion object {
        const val COOLDOWN_WINDOWS = 2
        const val OVERLOAD_WINDOWS_BEFORE_DOWNGRADE = 2
        const val MINIMUM_PROFILE_OVERLOAD_WINDOWS = 2

        fun assess(
            current: VideoEncoderStatsSample,
            previous: VideoEncoderStatsSample,
            activeProfile: VideoStreamProfile,
        ): VideoEncoderAssessment {
            val elapsedSeconds =
                ((current.timestampUs - previous.timestampUs) / 1_000_000.0)
                    .takeIf { duration -> duration > 0.0 }
                    ?: 1.0
            val encodedFramesDelta =
                ((current.framesEncoded ?: previous.framesEncoded ?: 0L) - (previous.framesEncoded ?: 0L))
                    .coerceAtLeast(0L)
            val encodedFps = encodedFramesDelta / elapsedSeconds
            val isCpuLimited = current.qualityLimitationReason.equals("cpu", ignoreCase = true)
            val isEncodedFpsTooLow = encodedFps < activeProfile.fps * 0.75
            val lowerTierFps =
                StreamConfig.FPS_OPTIONS
                    .filter { option -> option < activeProfile.fps }
                    .maxOrNull()
            val isReportedFpsTooLow =
                lowerTierFps != null &&
                    (current.framesPerSecond ?: Double.MAX_VALUE) < activeProfile.fps * 0.8
            val reasonSummary =
                buildString {
                    append("encodedFps=")
                    append(String.format("%.2f", encodedFps))
                    append(", targetFps=")
                    append(activeProfile.fps)
                    append(", reportedFps=")
                    append(current.framesPerSecond?.let { value -> String.format("%.2f", value) } ?: "n/a")
                    append(", qualityLimitationReason=")
                    append(current.qualityLimitationReason ?: "n/a")
                }
            return VideoEncoderAssessment(
                encodedFps = encodedFps,
                reportedFps = current.framesPerSecond,
                qualityLimitationReason = current.qualityLimitationReason,
                overloaded = isCpuLimited || isEncodedFpsTooLow || isReportedFpsTooLow,
                reasonSummary = reasonSummary,
            )
        }
    }
}

private val VideoResolution.pixelCount: Int
    get() = width * height
