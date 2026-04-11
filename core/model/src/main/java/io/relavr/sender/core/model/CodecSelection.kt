package io.relavr.sender.core.model

data class CodecSelection(
    val requested: CodecPreference,
    val resolved: CodecPreference,
    val fellBack: Boolean,
)
