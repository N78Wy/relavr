package io.relavr.sender.platform.mediacodec

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.session.CodecPolicy
import io.relavr.sender.core.session.SenderException

class DefaultCodecPolicy : CodecPolicy {
    override fun select(
        preference: CodecPreference,
        capabilities: CapabilitySnapshot,
    ): CodecSelection {
        val resolved =
            when {
                capabilities.supports(preference) -> preference
                capabilities.supports(CodecPreference.H264) -> CodecPreference.H264
                else -> FALLBACK_ORDER.firstOrNull(capabilities::supports)
            } ?: throw SenderException(
                SenderError.CapabilityUnavailable("设备未报告可用的视频编码能力"),
            )

        return CodecSelection(
            requested = preference,
            resolved = resolved,
            fellBack = resolved != preference,
        )
    }

    private companion object {
        val FALLBACK_ORDER =
            listOf(
                CodecPreference.HEVC,
                CodecPreference.VP8,
                CodecPreference.VP9,
            )
    }
}
