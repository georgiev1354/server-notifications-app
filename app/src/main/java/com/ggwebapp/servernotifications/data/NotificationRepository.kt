package com.ggwebapp.servernotifications.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Пази известията в локална SQLite база и ги излъчва като [StateFlow],
 * така че списъкът в UI се обновява веднага при ново известие.
 */
class NotificationRepository private constructor(context: Context) {

    private val db = Helper(context.applicationContext)
    private val _notifications = MutableStateFlow<List<ServerNotification>>(emptyList())
    val notifications: StateFlow<List<ServerNotification>> = _notifications.asStateFlow()

    init {
        reload()
    }

    @Synchronized
    fun insert(
        title: String,
        body: String,
        level: Level,
        category: Category,
        server: String,
        timestamp: Long,
    ): Long {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_BODY, body)
            put(COL_LEVEL, level.key)
            put(COL_CATEGORY, category.key)
            put(COL_SERVER, server)
            put(COL_TS, timestamp)
            put(COL_READ, 0)
        }
        val id = db.writableDatabase.insert(TABLE, null, values)
        trim()
        reload()
        return id
    }

    fun get(id: Long): ServerNotification? = _notifications.value.firstOrNull { it.id == id }

    @Synchronized
    fun markRead(id: Long) {
        db.writableDatabase.execSQL("UPDATE $TABLE SET $COL_READ = 1 WHERE $COL_ID = ?", arrayOf(id))
        reload()
    }

    @Synchronized
    fun markAllRead() {
        db.writableDatabase.execSQL("UPDATE $TABLE SET $COL_READ = 1")
        reload()
    }

    @Synchronized
    fun delete(id: Long) {
        db.writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
        reload()
    }

    @Synchronized
    fun deleteAll() {
        db.writableDatabase.delete(TABLE, null, null)
        reload()
    }

    /** Пазим най-много [MAX_ROWS] известия – по-старите се изтриват. */
    private fun trim() {
        db.writableDatabase.execSQL(
            "DELETE FROM $TABLE WHERE $COL_ID NOT IN " +
                "(SELECT $COL_ID FROM $TABLE ORDER BY $COL_ID DESC LIMIT $MAX_ROWS)"
        )
    }

    private fun reload() {
        val list = mutableListOf<ServerNotification>()
        db.readableDatabase.query(TABLE, null, null, null, null, null, "$COL_ID DESC").use { c ->
            while (c.moveToNext()) list += c.toNotification()
        }
        _notifications.value = list
    }

    private fun Cursor.toNotification() = ServerNotification(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        body = getString(getColumnIndexOrThrow(COL_BODY)),
        level = Level.from(getString(getColumnIndexOrThrow(COL_LEVEL))),
        category = Category.from(getString(getColumnIndexOrThrow(COL_CATEGORY))),
        server = getString(getColumnIndexOrThrow(COL_SERVER)),
        timestamp = getLong(getColumnIndexOrThrow(COL_TS)),
        read = getInt(getColumnIndexOrThrow(COL_READ)) == 1,
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, "notifications.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TITLE TEXT NOT NULL,
                    $COL_BODY TEXT NOT NULL,
                    $COL_LEVEL TEXT NOT NULL,
                    $COL_CATEGORY TEXT NOT NULL,
                    $COL_SERVER TEXT NOT NULL,
                    $COL_TS INTEGER NOT NULL,
                    $COL_READ INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val TABLE = "notifications"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_BODY = "body"
        private const val COL_LEVEL = "level"
        private const val COL_CATEGORY = "category"
        private const val COL_SERVER = "server"
        private const val COL_TS = "ts"
        private const val COL_READ = "read"
        private const val MAX_ROWS = 2000

        @Volatile
        private var instance: NotificationRepository? = null

        fun get(context: Context): NotificationRepository =
            instance ?: synchronized(this) {
                instance ?: NotificationRepository(context).also { instance = it }
            }
    }
}
