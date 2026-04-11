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
        val defaultCodec =
            capabilities.defaultCodec.takeIf(capabilities::supports)
                ?: CapabilitySnapshot
                    .resolveDefaultCodec(capabilities.supportedCodecs)
                    .takeIf(capabilities::supports)
        val resolved =
            when {
                capabilities.supports(preference) -> preference
                defaultCodec != null -> defaultCodec
                else -> null
            } ?: throw SenderException(
                SenderError.CapabilityUnavailable("设备未报告可用的视频编码能力"),
            )

        return CodecSelection(
            requested = preference,
            resolved = resolved,
            fellBack = resolved != preference,
        )
    }
}
