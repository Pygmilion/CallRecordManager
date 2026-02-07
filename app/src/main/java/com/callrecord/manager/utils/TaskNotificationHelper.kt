package com.callrecord.manager.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callrecord.manager.MainActivity
import com.callrecord.manager.R

/**
 * Helper for managing task progress notifications.
 * Shows/updates/clears system notifications for ASR transcription and LLM minute generation.
 */
object TaskNotificationHelper {

    private const val CHANNEL_ID = "task_progress"
    private const val CHANNEL_NAME = "任务进度"
    private const val CHANNEL_DESC = "显示转写和纪要生成任务的进度"
    private const val NOTIFICATION_ID_PROGRESS = 1001
    private const val NOTIFICATION_ID_RESULT = 1002

    /**
     * Create the notification channel. Must be called once at app start (e.g. in onCreate).
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Show or update a progress notification (ongoing, not dismissible).
     */
    fun showProgressNotification(context: Context, title: String, text: String) {
        val pendingIntent = buildLaunchIntent(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification)
        } catch (e: SecurityException) {
            // Permission not granted; silently ignore
            AppLogger.w("Notification", "POST_NOTIFICATIONS permission not granted")
        }
    }

    /**
     * Show a success notification. Auto-cancels after tap.
     */
    fun showSuccessNotification(context: Context, text: String) {
        cancelProgressNotification(context)

        val pendingIntent = buildLaunchIntent(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("任务完成")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setTimeoutAfter(5000)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_RESULT, notification)
        } catch (e: SecurityException) {
            AppLogger.w("Notification", "POST_NOTIFICATIONS permission not granted")
        }
    }

    /**
     * Show a failure notification. Tapping returns to app.
     */
    fun showFailureNotification(context: Context, text: String) {
        cancelProgressNotification(context)

        val pendingIntent = buildLaunchIntent(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("任务失败")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_RESULT, notification)
        } catch (e: SecurityException) {
            AppLogger.w("Notification", "POST_NOTIFICATIONS permission not granted")
        }
    }

    /**
     * Cancel the ongoing progress notification.
     */
    fun cancelProgressNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS)
    }

    private fun buildLaunchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
