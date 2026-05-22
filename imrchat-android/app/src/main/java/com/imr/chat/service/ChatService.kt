package com.imr.chat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.imr.chat.MainActivity

class ChatService : Service() {

    companion object {
        const val CHANNEL_ID = "imrchat_service"
        const val MSG_CHANNEL_ID = "imrchat_messages"
        const val NOTIFICATION_ID = 1
        const val MSG_NOTIFICATION_ID = 100

        fun start(context: Context) {
            val intent = Intent(context, ChatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Foreground service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "IMRChat 后台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 WebSocket 连接"
            }
            manager.createNotificationChannel(serviceChannel)

            // Message notification channel
            val msgChannel = NotificationChannel(
                MSG_CHANNEL_ID,
                "新消息通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到新消息时通知"
                enableVibration(true)
            }
            manager.createNotificationChannel(msgChannel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("IMRChat")
            .setContentText("保持连接中...")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun showMessageNotification(title: String, content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, MSG_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(MSG_NOTIFICATION_ID, notification)
    }
}
