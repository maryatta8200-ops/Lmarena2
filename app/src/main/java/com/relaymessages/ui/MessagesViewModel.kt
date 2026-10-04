package com.relaymessages.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relaymessages.data.AppDatabase
import com.relaymessages.data.ContactRecord
import com.relaymessages.data.LocalLearningEngine
import com.relaymessages.data.ReplySuggestion
import com.relaymessages.data.SmsRecord
import com.relaymessages.data.ThreadRecord
import com.relaymessages.data.TrainingImporter
import com.relaymessages.sms.SmsRepository
import com.relaymessages.sms.SmsRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val database = AppDatabase.get(app)
    private val smsRepository = SmsRepository(app)

    var threads: List<ThreadRecord> by mutableStateOf(emptyList())
        private set
    var contacts: List<ContactRecord> by mutableStateOf(emptyList())
        private set
    var activeMessages: List<SmsRecord> by mutableStateOf(emptyList())
        private set
    var autoReplyEnabled: Boolean by mutableStateOf(false)
        private set
    var trainingCount: Int by mutableStateOf(0)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var notice: String? by mutableStateOf(null)
        private set
    var previewSuggestion: ReplySuggestion? by mutableStateOf(null)
        private set
    var refreshVersion: Int by mutableStateOf(0)
        private set

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                if (app.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    runCatching { smsRepository.syncSystemMessages() }
                }
                Snapshot(
                    threads = runCatching { database.threads() }.getOrDefault(emptyList()),
                    contacts = runCatching { database.contacts() }.getOrDefault(emptyList()),
                    trainingCount = runCatching { database.trainingExamples().size }.getOrDefault(0),
                    autoReply = runCatching { database.isAutoReplyEnabled() }.getOrDefault(false)
                )
            }
            threads = snapshot.threads
            contacts = snapshot.contacts
            trainingCount = snapshot.trainingCount
            autoReplyEnabled = snapshot.autoReply
            refreshVersion++
        }
    }

    fun openConversation(phone: String) {
        viewModelScope.launch {
            activeMessages = withContext(Dispatchers.IO) {
                database.markConversationRead(phone)
                database.messagesForPhone(phone)
            }
            refresh()
        }
    }

    fun refreshConversation(phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { database.messagesForPhone(phone) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { activeMessages = result }
        }
    }

    fun sendMessage(phone: String, body: String): Boolean {
        if (!SmsRole.isDefault(app)) {
            showNotice("Make Relay the default SMS app in Settings before sending SMS.")
            return false
        }
        if (app.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            showNotice("Grant SMS permission in Settings before sending.")
            return false
        }
        if (body.isBlank()) return false
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { smsRepository.send(phone, body) }
            }
            busy = false
            result.onSuccess {
                activeMessages = withContext(Dispatchers.IO) { database.messagesForPhone(phone) }
                refresh()
            }.onFailure { exception ->
                showNotice(exception.message ?: "The SMS could not be sent.")
            }
        }
        return true
    }

    fun saveContact(contact: ContactRecord): Boolean {
        val normalized = AppDatabase.normalizePhone(contact.phone)
        if (contact.name.isBlank() || normalized.length < 5) {
            showNotice("Enter a name and a valid phone number (including country code when needed).")
            return false
        }
        val duplicate = contacts.firstOrNull {
            it.id != contact.id && AppDatabase.normalizePhone(it.phone) == normalized
        }
        if (duplicate != null) {
            showNotice("That number is already saved for ${duplicate.name}.")
            return false
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { database.saveContact(contact) }
                .onSuccess { withContext(Dispatchers.Main) { refresh() } }
                .onFailure { withContext(Dispatchers.Main) { showNotice("Could not save this number.") } }
        }
        return true
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { database.deleteContact(id) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun setContactAutoReply(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { database.setContactAutoReply(id, enabled) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun setGlobalAutoReply(enabled: Boolean) {
        if (enabled) {
            if (!SmsRole.isDefault(app)) {
                showNotice("Choose Relay as the default SMS app before enabling auto-reply.")
                return
            }
            val required = listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
            if (required.any { app.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                showNotice("Grant SMS send and receive permissions before enabling auto-reply.")
                return
            }
            if (trainingCount == 0) {
                showNotice("Import or add training examples before enabling auto-reply.")
                return
            }
            if (contacts.none { it.autoReplyEnabled }) {
                showNotice("Enable auto-reply for at least one saved number first.")
                return
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { database.setAutoReplyEnabled(enabled) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun importTraining(uri: android.net.Uri) {
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { TrainingImporter.importFile(app, database, uri) }
            }
            busy = false
            result.onSuccess { summary ->
                showNotice("Imported ${summary.added} examples (${summary.duplicates} duplicates skipped).")
                refresh()
            }.onFailure { exception ->
                showNotice(exception.message ?: "Training file could not be imported.")
            }
        }
    }

    fun clearTraining() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                database.clearTrainingExamples()
                database.setAutoReplyEnabled(false)
            }
            withContext(Dispatchers.Main) {
                showNotice("Local training examples cleared. Auto-reply is off.")
                refresh()
            }
        }
    }

    fun previewReply(message: String) {
        if (message.isBlank()) {
            previewSuggestion = null
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LocalLearningEngine(database.trainingExamples()).suggest(message) }.getOrNull()
            }
            previewSuggestion = result
        }
    }

    fun importDeviceContacts() {
        if (app.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            showNotice("Allow Contacts permission, then try importing again.")
            return
        }
        viewModelScope.launch {
            busy = true
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val existing = database.contacts().map { AppDatabase.normalizePhone(it.phone) }.toHashSet()
                    val cursor = app.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
                    ) ?: return@runCatching 0
                    var added = 0
                    cursor.use {
                        val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (it.moveToNext() && added < MAX_IMPORTED_CONTACTS) {
                            val name = it.getString(nameIndex)?.trim().orEmpty()
                            val phone = it.getString(numberIndex)?.trim().orEmpty()
                            val key = AppDatabase.normalizePhone(phone)
                            if (name.isBlank() || key.length < 5 || !existing.add(key)) continue
                            database.saveContact(ContactRecord(name = name, phone = phone, category = "Imported"))
                            added++
                        }
                    }
                    added
                }.getOrDefault(0)
            }
            busy = false
            showNotice("Added $count device contacts. Review their details in Numbers.")
            refresh()
        }
    }

    fun showNotice(text: String) {
        notice = text
    }

    fun dismissNotice() {
        notice = null
    }

    private data class Snapshot(
        val threads: List<ThreadRecord>,
        val contacts: List<ContactRecord>,
        val trainingCount: Int,
        val autoReply: Boolean
    )

    companion object {
        private const val MAX_IMPORTED_CONTACTS = 1_000
    }
}
