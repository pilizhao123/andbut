package com.example.autoclicker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.autoclicker.R

/**
 * 通知工具：为前台服务创建通知渠道并构建持续通知。
 */
object NotificationUtils {

    const val CHANNEL_ID = "autoclicker_channel"
    private const val CHANNEL_NAME = "自动连点服务"

    /** 创建前台服务所需的通知渠道（Android 8.0+ 必需）。 */
    fun createChannel(context: Context) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于保持自动连点服务前台运行"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 构建前台服务通知。 */
    fun buildNotification(context: Context, contentText: String): android.app.Notification {
        createChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}
