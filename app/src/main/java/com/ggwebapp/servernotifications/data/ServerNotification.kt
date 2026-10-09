package com.ggwebapp.servernotifications.data

/** Едно известие, получено от сървъра. */
data class ServerNotification(
    val id: Long,
    val title: String,
    val body: String,
    val level: Level,
    val category: Category,
    val server: String,
    /** Време на събитието на сървъра (epoch millis). */
    val timestamp: Long,
    val read: Boolean,
)

enum class Level(val key: String) {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: INFO
    }
}

enum class Category(val key: String, val label: String, val emoji: String) {
    SECURITY("security", "Сигурност", "🔐"),
    SYSTEM("system", "Система", "🖥️"),
    SERVICE("service", "Услуги", "⚙️"),
    REPORT("report", "Отчети", "📊"),
    CUSTOM("custom", "Други", "🔔");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}
