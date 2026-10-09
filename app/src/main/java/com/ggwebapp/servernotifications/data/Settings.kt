package com.ggwebapp.servernotifications.data

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging

/** Настройки на приложението: за кой FCM topic сме абонирани. */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val topic: String?
        get() = prefs.getString(KEY_TOPIC, null)

    /**
     * Абонира се за нов topic (и се отписва от стария).
     * [onResult] получава null при успех или текст на грешката.
     */
    fun subscribe(newTopic: String, onResult: (String?) -> Unit) {
        val topic = newTopic.trim()
        if (!TOPIC_REGEX.matches(topic)) {
            onResult("Невалиден topic. Разрешени са латински букви, цифри и -_.~%")
            return
        }
        val old = this.topic
        if (old != null && old != topic) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(old)
        }
        FirebaseMessaging.getInstance().subscribeToTopic(topic).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                prefs.edit { putString(KEY_TOPIC, topic) }
                onResult(null)
            } else {
                onResult(task.exception?.localizedMessage ?: "Неуспешен абонамент. Проверете интернет връзката.")
            }
        }
    }

    fun unsubscribe(onResult: (String?) -> Unit) {
        val old = topic ?: return onResult(null)
        FirebaseMessaging.getInstance().unsubscribeFromTopic(old).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                prefs.edit { remove(KEY_TOPIC) }
                onResult(null)
            } else {
                onResult(task.exception?.localizedMessage ?: "Неуспешно отписване.")
            }
        }
    }

    /** Повторен абонамент при нов FCM токен (напр. след преинсталиране на Play услугите). */
    fun resubscribe() {
        topic?.let { FirebaseMessaging.getInstance().subscribeToTopic(it) }
    }

    companion object {
        private const val KEY_TOPIC = "topic"
        private val TOPIC_REGEX = Regex("[a-zA-Z0-9\\-_.~%]{1,900}")
    }
}
