package com.relaymessages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.relaymessages.data.AppDatabase
import com.relaymessages.data.LocalLearningEngine
import java.util.concurrent.Executors

class SmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            .orEmpty()
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        ReceiverWork.executor.execute {
            try {
                val address = messages.firstNotNullOfOrNull { it.originatingAddress } ?: return@execute
                val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
                if (body.isEmpty()) return@execute
                val timestamp = messages.minOfOrNull { it.timestampMillis }
                    ?: System.currentTimeMillis()
                val database = AppDatabase.get(appContext)
                SmsRepository(appContext).recordIncoming(address, body, timestamp)
                NotificationHelper.incoming(appContext, address, body)

                if (!SmsRole.isDefault(appContext) ||
                    appContext.checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    !database.isAutoReplyEnabled()
                ) return@execute

                val contact = database.contactByPhone(address) ?: return@execute
                if (!contact.autoReplyEnabled) return@execute
                if (System.currentTimeMillis() - contact.lastAutoReplyAt < AUTO_REPLY_COOLDOWN_MS) return@execute
                if (containsOptOut(body)) return@execute

                val examples = database.trainingExamples()
                if (examples.isEmpty()) return@execute
                val suggestion = LocalLearningEngine(examples).suggest(body) ?: return@execute
                if (suggestion.confidence < LocalLearningEngine.AUTO_REPLY_MINIMUM_CONFIDENCE) return@execute

                SmsRepository(appContext).send(address, suggestion.reply, autoReply = true)
                database.recordAutoReplyTime(contact.id, System.currentTimeMillis())
            } catch (_: SecurityException) {
                // Permissions can change while the receiver is running; never retry in the background.
            } catch (_: Exception) {
                // Do not crash Android's broadcast process for malformed or unsupported messages.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun containsOptOut(message: String): Boolean = OPT_OUT_PATTERN.containsMatchIn(message)

    companion object {
        private const val AUTO_REPLY_COOLDOWN_MS = 24L * 60 * 60 * 1_000
        private val OPT_OUT_PATTERN = Regex("\\b(stop|unsubscribe|cancel|end|quit)\\b", RegexOption.IGNORE_CASE)
    }
}

class MmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        NotificationHelper.mmsUnsupported(context.applicationContext)
    }
}

private object ReceiverWork {
    val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "relay-sms-delivery").apply { isDaemon = true }
    }
}
