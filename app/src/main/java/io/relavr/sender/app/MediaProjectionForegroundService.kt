package io.relavr.sender.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.relavr.sender.core.common.AndroidAppLogger
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.session.StreamingSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MediaProjectionForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var isForegroundActive: Boolean = false

    private val appContainer: AppContainer
        get() = (application as RelavrApplication).appContainer

    private val sessionEngine: StreamingSessionController
        get() = appContainer.sessionEngine

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        notificationJob =
            serviceScope.launch {
                sessionEngine.observeState().collect { snapshot ->
                    if (isForegroundActive) {
                        updateForegroundNotification(snapshot)
                    }
                }
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.toStreamConfig()
                if (config == null) {
                    AndroidAppLogger.error(TAG, "前台推流服务缺少必要的 StreamConfig")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }

                // Android 14+ 要求先进入 mediaProjection 前台服务，再创建 MediaProjection 会话。
                enterForeground(sessionEngine.observeState().value)
                serviceScope.launch {
                    handleStart(startId = startId, config = config)
                }
            }

            ACTION_STOP -> {
                serviceScope.launch {
                    handleStop(startId)
                }
            }

            else -> {
                AndroidAppLogger.error(TAG, "收到未知的前台推流服务命令: ${intent?.action}")
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        exitForeground()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handleStart(
        startId: Int,
        config: StreamConfig,
    ) {
        runCatching {
            sessionEngine.start(config)
        }.onFailure { throwable ->
            AndroidAppLogger.error(TAG, "前台推流服务启动会话时发生未处理异常", throwable)
        }

        if (!sessionEngine.observeState().value.isStreaming) {
            exitForeground()
            stopSelfResult(startId)
        }
    }

    private suspend fun handleStop(startId: Int) {
        runCatching {
            sessionEngine.stop()
        }.onFailure { throwable ->
            AndroidAppLogger.error(TAG, "前台推流服务停止会话时发生未处理异常", throwable)
        }
        exitForeground()
        stopSelfResult(startId)
    }

    private fun enterForeground(snapshot: StreamingSessionSnapshot) {
        updateForegroundNotification(snapshot)
        isForegroundActive = true
    }

    private fun updateForegroundNotification(snapshot: StreamingSessionSnapshot) {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(snapshot),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    private fun exitForeground() {
        if (!isForegroundActive) {
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundActive = false
    }

    private fun ensureNotificationChannel() {
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.streaming_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.streaming_notification_channel_description)
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(snapshot: StreamingSessionSnapshot) =
        NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(getString(R.string.streaming_notification_title))
            .setContentText(snapshot.toNotificationText())
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun StreamingSessionSnapshot.toNotificationText(): String =
        statusDetail?.takeIf { it.isNotBlank() } ?: when {
            isStreaming -> getString(R.string.streaming_notification_streaming)
            captureState == CaptureState.RequestingPermission ->
                getString(R.string.streaming_notification_waiting_permission)
            captureState == CaptureState.Starting || publishState == PublishState.Preparing ->
                getString(R.string.streaming_notification_starting)
            captureState == CaptureState.Stopping || publishState == PublishState.Stopping ->
                getString(R.string.streaming_notification_stopping)
            else -> getString(R.string.streaming_notification_preparing)
        }

    private fun Intent.toStreamConfig(): StreamConfig? {
        val codecName = getStringExtra(EXTRA_CODEC_NAME) ?: return null
        return runCatching {
            StreamConfig(
                videoEnabled = getBooleanExtra(EXTRA_VIDEO_ENABLED, true),
                audioEnabled = getBooleanExtra(EXTRA_AUDIO_ENABLED, true),
                codecPreference = CodecPreference.valueOf(codecName),
                resolution =
                    VideoResolution(
                        width = getIntExtra(EXTRA_RESOLUTION_WIDTH, 1280),
                        height = getIntExtra(EXTRA_RESOLUTION_HEIGHT, 720),
                    ),
                fps = getIntExtra(EXTRA_FPS, 30),
                bitrateKbps = getIntExtra(EXTRA_BITRATE_KBPS, 4000),
                signalingEndpoint =
                    getStringExtra(EXTRA_SIGNALING_ENDPOINT)
                        ?: StreamConfig().signalingEndpoint,
                sessionId =
                    getStringExtra(EXTRA_SESSION_ID)
                        ?: StreamConfig().sessionId,
                iceServers =
                    getStringArrayListExtra(EXTRA_ICE_SERVERS)
                        ?.toList()
                        ?: emptyList(),
            )
        }.getOrNull()
    }

    companion object {
        private const val TAG = "StreamingSession"
        private const val ACTION_START = "io.relavr.sender.action.START_STREAMING_SESSION"
        private const val ACTION_STOP = "io.relavr.sender.action.STOP_STREAMING_SESSION"
        private const val EXTRA_VIDEO_ENABLED = "extra_video_enabled"
        private const val EXTRA_AUDIO_ENABLED = "extra_audio_enabled"
        private const val EXTRA_CODEC_NAME = "extra_codec_name"
        private const val EXTRA_RESOLUTION_WIDTH = "extra_resolution_width"
        private const val EXTRA_RESOLUTION_HEIGHT = "extra_resolution_height"
        private const val EXTRA_FPS = "extra_fps"
        private const val EXTRA_BITRATE_KBPS = "extra_bitrate_kbps"
        private const val EXTRA_SIGNALING_ENDPOINT = "extra_signaling_endpoint"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_ICE_SERVERS = "extra_ice_servers"
        private const val NOTIFICATION_CHANNEL_ID = "streaming_session"
        private const val NOTIFICATION_ID = 1001

        fun createStartIntent(
            context: Context,
            config: StreamConfig,
        ): Intent =
            Intent(context, MediaProjectionForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_VIDEO_ENABLED, config.videoEnabled)
                .putExtra(EXTRA_AUDIO_ENABLED, config.audioEnabled)
                .putExtra(EXTRA_CODEC_NAME, config.codecPreference.name)
                .putExtra(EXTRA_RESOLUTION_WIDTH, config.resolution.width)
                .putExtra(EXTRA_RESOLUTION_HEIGHT, config.resolution.height)
                .putExtra(EXTRA_FPS, config.fps)
                .putExtra(EXTRA_BITRATE_KBPS, config.bitrateKbps)
                .putExtra(EXTRA_SIGNALING_ENDPOINT, config.signalingEndpoint)
                .putExtra(EXTRA_SESSION_ID, config.sessionId)
                .putStringArrayListExtra(EXTRA_ICE_SERVERS, ArrayList(config.iceServers))

        fun createStopIntent(context: Context): Intent =
            Intent(context, MediaProjectionForegroundService::class.java).setAction(ACTION_STOP)
    }
}

internal class AndroidForegroundServiceCommandDispatcher(
    private val context: Context,
) : ForegroundServiceCommandDispatcher {
    override fun startSession(config: StreamConfig) {
        ContextCompat.startForegroundService(
            context,
            MediaProjectionForegroundService.createStartIntent(context, config),
        )
    }

    override fun stopSession() {
        context.startService(MediaProjectionForegroundService.createStopIntent(context))
    }
}
