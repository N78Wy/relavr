package io.relavr.sender.core.common

import android.util.Log

interface AppLogger {
    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

object AndroidAppLogger : AppLogger {
    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        Log.e(tag, message, throwable)
    }
}
