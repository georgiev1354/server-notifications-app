package com.ggwebapp.servernotifications.push

import com.ggwebapp.servernotifications.data.Category
import com.ggwebapp.servernotifications.data.Level
import com.ggwebapp.servernotifications.data.NotificationRepository
import com.ggwebapp.servernotifications.data.Settings
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Получава data съобщенията от FCM (изпратени от push-notify на сървъра),
 * записва ги в базата и показва системно известие.
 * Извиква се и когато приложението е затворено, защото съобщенията са data-only с висок приоритет.
 */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val timestamp = data["ts"]?.toLongOrNull()?.times(1000) ?: message.sentTime

        val repository = NotificationRepository.get(this)
        val id = repository.insert(
            title = title,
            body = body,
            level = Level.from(data["level"]),
            category = Category.from(data["category"]),
            server = data["server"].orEmpty(),
            timestamp = timestamp,
        )
        repository.get(id)?.let { Notifier.show(this, it) }
    }

    override fun onNewToken(token: String) {
        Settings(this).resubscribe()
    }
}
