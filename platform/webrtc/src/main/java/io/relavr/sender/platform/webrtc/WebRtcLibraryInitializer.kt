package io.relavr.sender.platform.webrtc

import android.content.Context
import org.webrtc.PeerConnectionFactory

class WebRtcLibraryInitializer(
    private val initializeBlock: () -> Unit,
) {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) {
            return
        }
        synchronized(this) {
            if (initialized) {
                return
            }
            initializeBlock()
            initialized = true
        }
    }

    companion object {
        fun create(context: Context): WebRtcLibraryInitializer {
            val appContext = context.applicationContext
            return WebRtcLibraryInitializer {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(appContext)
                        .createInitializationOptions(),
                )
            }
        }
    }
}
