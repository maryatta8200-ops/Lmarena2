package com.relaymessages.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DATABASE_NAME = "relay_private.db"
private const val DATABASE_VERSION = 1

/** Private on-device store. Calls are made from a background dispatcher/executor. */
class AppDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE contacts (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                normalized_phone TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                profession TEXT NOT NULL DEFAULT '',
                purpose TEXT NOT NULL DEFAULT '',
                whatsapp INTEGER NOT NULL DEFAULT 0,
                auto_reply INTEGER NOT NULL DEFAULT 0,
                last_auto_reply INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX contacts_normalized_phone_idx ON contacts(normalized_phone)")
        db.execSQL(
            """CREATE TABLE messages (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                provider_id INTEGER UNIQUE,
                phone TEXT NOT NULL,
                normalized_phone TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                direction INTEGER NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0,
                is_auto_reply INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX messages_phone_timestamp_idx ON messages(normalized_phone, timestamp)")
        db.execSQL(
            """CREATE TABLE training_examples (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                intent TEXT NOT NULL,
                input_text TEXT NOT NULL,
                reply_text TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(intent, input_text, reply_text) ON CONFLICT IGNORE
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE settings (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT NOT NULL
            )""".trimIndent()
        )
        db.execSQL("INSERT INTO settings(setting_key, setting_value) VALUES('auto_reply_enabled', 'false')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema changes must be added as explicit, forward-only migrations before a public release.
    }

    fun saveContact(contact: ContactRecord): Long {
        val values = ContentValues().apply {
            put("name", contact.name.trim())
            put("phone", contact.phone.trim())
            put("normalized_phone", normalizePhone(contact.phone))
            put("category", contact.category.trim())
            put("profession", contact.profession.trim())
            put("purpose", contact.purpose.trim())
            put("whatsapp", if (contact.whatsappEnabled) 1 else 0)
            put("auto_reply", if (contact.autoReplyEnabled) 1 else 0)
            put("last_auto_reply", contact.lastAutoReplyAt)
        }
        return writableDatabase.let { db ->
            if (contact.id == 0L) {
                db.insertOrThrow("contacts", null, values)
            } else {
                db.update("contacts", values, "_id = ?", arrayOf(contact.id.toString()))
                contact.id
            }
        }
    }

    fun deleteContact(id: Long) {
        writableDatabase.delete("contacts", "_id = ?", arrayOf(id.toString()))
    }

    fun contacts(): List<ContactRecord> {
        val result = mutableListOf<ContactRecord>()
        readableDatabase.query(
            "contacts",
            CONTACT_COLUMNS,
            null,
            null,
            null,
            null,
            "name COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toContactRecord()
        }
        return result
    }

    fun contactByPhone(phone: String): ContactRecord? {
        val normalized = normalizePhone(phone)
        if (normalized.isEmpty()) return null
        readableDatabase.query(
            "contacts",
            CONTACT_COLUMNS,
            "normalized_phone = ?",
            arrayOf(normalized),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toContactRecord() else null
        }
    }

    fun contactById(id: Long): ContactRecord? = readableDatabase.query(
        "contacts",
        CONTACT_COLUMNS,
        "_id = ?",
        arrayOf(id.toString()),
        null,
        null,
        null,
        "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toContactRecord() else null }

    fun insertMessage(
        phone: String,
        body: String,
        timestamp: Long,
        direction: MessageDirection,
        providerId: Long? = null,
        isAutoReply: Boolean = false,
        isRead: Boolean = false
    ): Long {
        if (providerId != null) {
            readableDatabase.query(
                "messages",
                arrayOf("_id"),
                "provider_id = ?",
                arrayOf(providerId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        }
        val values = ContentValues().apply {
            if (providerId != null) put("provider_id", providerId)
            put("phone", phone.trim())
            put("normalized_phone", normalizePhone(phone))
            put("body", body)
            put("timestamp", timestamp)
            put("direction", direction.value)
            put("is_read", if (isRead) 1 else 0)
            put("is_auto_reply", if (isAutoReply) 1 else 0)
        }
        return writableDatabase.insertOrThrow("messages", null, values)
    }

    fun messagesForPhone(phone: String, limit: Int = 500): List<SmsRecord> {
        val result = mutableListOf<SmsRecord>()
        readableDatabase.query(
            "messages",
            MESSAGE_COLUMNS,
            "normalized_phone = ?",
            arrayOf(normalizePhone(phone)),
            null,
            null,
            "timestamp DESC, _id DESC",
            limit.coerceIn(1, 2_000).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toSmsRecord()
        }
        return result
    }

    fun threads(): List<ThreadRecord> {
        val sql = """SELECT m.phone, c.name, m.body, m.timestamp, m.direction,
            (SELECT COUNT(*) FROM messages u WHERE u.normalized_phone = m.normalized_phone AND u.is_read = 0 AND u.direction = 0) AS unread_count
            FROM messages m
            LEFT JOIN contacts c ON c.normalized_phone = m.normalized_phone
            WHERE m._id = (
                SELECT latest._id FROM messages latest
                WHERE latest.normalized_phone = m.normalized_phone
                ORDER BY latest.timestamp DESC, latest._id DESC LIMIT 1
            )
            ORDER BY m.timestamp DESC"""
        val result = mutableListOf<ThreadRecord>()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            val phoneIndex = cursor.getColumnIndexOrThrow("phone")
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val bodyIndex = cursor.getColumnIndexOrThrow("body")
            val timeIndex = cursor.getColumnIndexOrThrow("timestamp")
            val directionIndex = cursor.getColumnIndexOrThrow("direction")
            val unreadIndex = cursor.getColumnIndexOrThrow("unread_count")
            while (cursor.moveToNext()) {
                result += ThreadRecord(
                    phone = cursor.getString(phoneIndex) ?: "",
                    displayName = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(phoneIndex).orEmpty(),
                    preview = cursor.getString(bodyIndex).orEmpty(),
                    timestamp = cursor.getLong(timeIndex),
                    lastMessageOutgoing = cursor.getInt(directionIndex) == MessageDirection.OUTGOING.value,
                    unreadCount = cursor.getInt(unreadIndex)
                )
            }
        }
        return result
    }

    fun markConversationRead(phone: String) {
        val values = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update(
            "messages",
            values,
            "normalized_phone = ? AND direction = 0",
            arrayOf(normalizePhone(phone))
        )
    }

    fun trainingExamples(): List<TrainingExample> {
        val result = mutableListOf<TrainingExample>()
        readableDatabase.query(
            "training_examples",
            arrayOf("intent", "input_text", "reply_text"),
            null,
            null,
            null,
            null,
            "_id ASC",
            "10000"
        ).use { cursor ->
            val intentIndex = cursor.getColumnIndexOrThrow("intent")
            val inputIndex = cursor.getColumnIndexOrThrow("input_text")
            val replyIndex = cursor.getColumnIndexOrThrow("reply_text")
            while (cursor.moveToNext()) {
                result += TrainingExample(
                    intent = cursor.getString(intentIndex),
                    input = cursor.getString(inputIndex),
                    reply = cursor.getString(replyIndex)
                )
            }
        }
        return result
    }

    fun addTrainingExamples(examples: List<TrainingExample>): Int {
        val db = writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            examples.forEach { example ->
                val values = ContentValues().apply {
                    put("intent", example.intent.trim())
                    put("input_text", example.input.trim())
                    put("reply_text", example.reply.trim())
                    put("created_at", System.currentTimeMillis())
                }
                if (db.insertWithOnConflict("training_examples", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun clearTrainingExamples() {
        writableDatabase.delete("training_examples", null, null)
    }

    fun isAutoReplyEnabled(): Boolean = readableDatabase.query(
        "settings",
        arrayOf("setting_value"),
        "setting_key = ?",
        arrayOf("auto_reply_enabled"),
        null,
        null,
        null,
        "1"
    ).use { cursor -> cursor.moveToFirst() && cursor.getString(0).equals("true", ignoreCase = true) }

    fun setAutoReplyEnabled(enabled: Boolean) {
        val values = ContentValues().apply {
            put("setting_key", "auto_reply_enabled")
            put("setting_value", enabled.toString())
        }
        writableDatabase.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setContactAutoReply(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put("auto_reply", if (enabled) 1 else 0) }
        writableDatabase.update("contacts", values, "_id = ?", arrayOf(id.toString()))
    }

    fun recordAutoReplyTime(id: Long, timestamp: Long) {
        val values = ContentValues().apply { put("last_auto_reply", timestamp) }
        writableDatabase.update("contacts", values, "_id = ?", arrayOf(id.toString()))
    }

    private fun android.database.Cursor.toContactRecord() = ContactRecord(
        id = getLong(getColumnIndexOrThrow("_id")),
        name = getString(getColumnIndexOrThrow("name")).orEmpty(),
        phone = getString(getColumnIndexOrThrow("phone")).orEmpty(),
        category = getString(getColumnIndexOrThrow("category")).orEmpty(),
        profession = getString(getColumnIndexOrThrow("profession")).orEmpty(),
        purpose = getString(getColumnIndexOrThrow("purpose")).orEmpty(),
        whatsappEnabled = getInt(getColumnIndexOrThrow("whatsapp")) == 1,
        autoReplyEnabled = getInt(getColumnIndexOrThrow("auto_reply")) == 1,
        lastAutoReplyAt = getLong(getColumnIndexOrThrow("last_auto_reply"))
    )

    private fun android.database.Cursor.toSmsRecord() = SmsRecord(
        id = getLong(getColumnIndexOrThrow("_id")),
        phone = getString(getColumnIndexOrThrow("phone")).orEmpty(),
        body = getString(getColumnIndexOrThrow("body")).orEmpty(),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        direction = MessageDirection.fromValue(getInt(getColumnIndexOrThrow("direction"))),
        isRead = getInt(getColumnIndexOrThrow("is_read")) == 1,
        isAutoReply = getInt(getColumnIndexOrThrow("is_auto_reply")) == 1
    )

    companion object {
        private val CONTACT_COLUMNS = arrayOf(
            "_id", "name", "phone", "category", "profession", "purpose", "whatsapp", "auto_reply", "last_auto_reply"
        )
        private val MESSAGE_COLUMNS = arrayOf(
            "_id", "phone", "body", "timestamp", "direction", "is_read", "is_auto_reply"
        )

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: AppDatabase(context).also { instance = it }
        }

        fun normalizePhone(phone: String): String = phone.filter { it.isDigit() }
    }
}

data class ContactRecord(
    val id: Long = 0,
    val name: String,
    val phone: String,
    val category: String = "",
    val profession: String = "",
    val purpose: String = "",
    val whatsappEnabled: Boolean = false,
    val autoReplyEnabled: Boolean = false,
    val lastAutoReplyAt: Long = 0
)

enum class MessageDirection(val value: Int) {
    INCOMING(0),
    OUTGOING(1);

    companion object {
        fun fromValue(value: Int): MessageDirection = if (value == OUTGOING.value) OUTGOING else INCOMING
    }
}

data class SmsRecord(
    val id: Long,
    val phone: String,
    val body: String,
    val timestamp: Long,
    val direction: MessageDirection,
    val isRead: Boolean,
    val isAutoReply: Boolean
)

data class ThreadRecord(
    val phone: String,
    val displayName: String,
    val preview: String,
    val timestamp: Long,
    val lastMessageOutgoing: Boolean,
    val unreadCount: Int
)
