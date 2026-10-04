package com.relaymessages.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import com.relaymessages.data.AppDatabase
import com.relaymessages.data.MessageDirection
import com.relaymessages.data.SmsRecord

class SmsRepository(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.get(appContext)

    /** Import a bounded view of the system SMS provider into Relay's local conversation index. */
    fun syncSystemMessages(maxRows: Int = 2_000): Int {
        val uri = Telephony.Sms.CONTENT_URI.buildUpon()
            .appendQueryParameter("limit", maxRows.coerceIn(1, 5_000).toString())
            .build()
        val cursor = appContext.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            null,
            null,
            "date DESC"
        ) ?: return 0
        var copied = 0
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndexOrThrow(Telephony.Sms.READ)
            var seen = 0
            while (it.moveToNext() && seen++ < maxRows) {
                val phone = it.getString(addressIndex)?.trim().orEmpty()
                val body = it.getString(bodyIndex).orEmpty()
                if (phone.isBlank() || body.isBlank()) continue
                val type = it.getInt(typeIndex)
                val direction = if (type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                    MessageDirection.OUTGOING
                } else {
                    MessageDirection.INCOMING
                }
                database.insertMessage(
                    phone = phone,
                    body = body,
                    timestamp = it.getLong(dateIndex),
                    direction = direction,
                    providerId = it.getLong(idIndex),
                    isRead = it.getInt(readIndex) != 0
                )
                copied++
            }
        }
        return copied
    }

    fun recordIncoming(phone: String, body: String, timestamp: Long): Long {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, phone)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        val providerUri: Uri? = runCatching {
            appContext.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.getOrNull()
        val providerId = providerUri?.lastPathSegment?.toLongOrNull()
        return database.insertMessage(
            phone = phone,
            body = body,
            timestamp = timestamp,
            direction = MessageDirection.INCOMING,
            providerId = providerId,
            isRead = false
        )
    }

    /** Sends only after the user has made Relay their default SMS handler and granted permission. */
    fun send(phone: String, text: String, autoReply: Boolean = false): SmsRecord {
        val number = phone.trim()
        val body = text.trim()
        require(number.filter { it.isDigit() }.length >= 5) { "Enter a valid phone number." }
        require(body.isNotEmpty()) { "Write a message first." }
        require(body.length <= MAX_SMS_CHARACTERS) { "Keep the message under $MAX_SMS_CHARACTERS characters." }

        val manager = appContext.getSystemService(SmsManager::class.java)
            ?: throw IllegalStateException("SMS service is not available on this device.")
        val parts = manager.divideMessage(body)
        if (parts.size <= 1) {
            manager.sendTextMessage(number, null, body, null, null)
        } else {
            manager.sendMultipartTextMessage(number, null, parts, null, null)
        }

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, number)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, now)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        val providerUri = runCatching {
            appContext.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        }.getOrNull()
        val providerId = providerUri?.lastPathSegment?.toLongOrNull()
        val id = database.insertMessage(
            phone = number,
            body = body,
            timestamp = now,
            direction = MessageDirection.OUTGOING,
            providerId = providerId,
            isAutoReply = autoReply,
            isRead = true
        )
        return SmsRecord(
            id = id,
            phone = number,
            body = body,
            timestamp = now,
            direction = MessageDirection.OUTGOING,
            isRead = true,
            isAutoReply = autoReply
        )
    }

    companion object {
        const val MAX_SMS_CHARACTERS = 1_000
    }
}
