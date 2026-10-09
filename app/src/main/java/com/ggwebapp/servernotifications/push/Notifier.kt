package com.ggwebapp.servernotifications.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ggwebapp.servernotifications.MainActivity
import com.ggwebapp.servernotifications.R
import com.ggwebapp.servernotifications.data.Level
import com.ggwebapp.servernotifications.data.ServerNotification

/** Системни известия – по един канал за всяко ниво, за да може потребителят да ги настройва поотделно. */
object Notifier {

    const val EXTRA_NOTIFICATION_ID = "notification_id"

    private fun channelId(level: Level) = "server_${level.key}"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(channelId(Level.CRITICAL), "Критични", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Спрели услуги, пълен диск и други спешни проблеми"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            },
            NotificationChannel(channelId(Level.WARNING), "Предупреждения", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Блокирани IP адреси, високо натоварване, нужен рестарт"
            },
            NotificationChannel(channelId(Level.INFO), "Информация", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Дневни отчети, SSH входове и други"
            },
        )
        manager.createNotificationChannels(channels)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, n: ServerNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, n.id)
        }
        val pending = PendingIntent.getActivity(
            context, n.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val color = when (n.level) {
            Level.CRITICAL -> 0xFFD32F2F.toInt()
            Level.WARNING -> 0xFFF57C00.toInt()
            Level.INFO -> 0xFF1976D2.toInt()
        }

        val notification = NotificationCompat.Builder(context, channelId(n.level))
            .setSmallIcon(R.drawable.ic_stat_server)
            .setColor(color)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setSubText(n.server)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setWhen(n.timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (n.level == Level.INFO) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(
                if (n.level == Level.CRITICAL) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_STATUS
            )
            .build()

        NotificationManagerCompat.from(context).notify(n.id.toInt(), notification)
    }

    fun cancel(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
