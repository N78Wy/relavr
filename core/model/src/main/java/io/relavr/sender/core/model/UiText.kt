package io.relavr.sender.core.model

import android.content.Context

data class UiText(
    val resId: Int,
    val args: List<Any> = emptyList(),
) {
    companion object {
        fun of(
            resId: Int,
            vararg args: Any,
        ): UiText = UiText(resId = resId, args = args.toList())
    }
}

fun Context.resolve(text: UiText): String = getString(text.resId, *text.args.toTypedArray())
