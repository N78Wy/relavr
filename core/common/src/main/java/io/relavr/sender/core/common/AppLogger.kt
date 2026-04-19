package io.relavr.sender.core.common

import android.util.Log

interface AppLogger {
    fun info(
        tag: String,
        message: String,
    )

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

object AndroidAppLogger : AppLogger {
    override fun info(
        tag: String,
        message: String,
    ) {
        Log.i(tag, message)
    }

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        Log.e(tag, message, throwable)
    }
}
