package com.relaymessages.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relaymessages.data.AppDatabase
import com.relaymessages.data.ContactRecord
import com.relaymessages.data.ReplySuggestion
import com.relaymessages.data.SmsRecord
import com.relaymessages.data.ThreadRecord
import com.relaymessages.sms.SmsRole
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsScreen(
    modifier: Modifier = Modifier,
    threads: List<ThreadRecord>,
    onOpen: (String) -> Unit,
    onCompose: () -> Unit,
    onGoToSettings: () -> Unit
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeading(
            title = "Messages",
            subtitle = "Your conversations, kept close",
            trailing = {
                TextButton(onClick = onCompose) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        )
        if (threads.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "A quieter inbox",
                detail = "Set Relay as your default SMS app to bring in your conversations. Messages stay on this device.",
                actionLabel = "Set up SMS",
                onAction = onGoToSettings
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(threads, key = { AppDatabase.normalizePhone(it.phone) }) { thread ->
                    ThreadCard(thread = thread, onClick = { onOpen(thread.phone) })
                }
            }
        }
    }
}

@Composable
fun NumbersScreen(
    modifier: Modifier = Modifier,
    contacts: List<ContactRecord>,
    onAdd: () -> Unit,
    onEdit: (ContactRecord) -> Unit,
    onOpen: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onToggleAutoReply: (Long, Boolean) -> Unit,
    onImportContacts: () -> Unit,
    busy: Boolean
) {
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    val categories = listOf("All") + contacts.map { it.category.trim() }
        .filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase(Locale.ROOT) }
    val shownContacts = if (selectedCategory == "All") contacts else contacts.filter { it.category == selectedCategory }

    Column(modifier.fillMaxSize()) {
        ScreenHeading(
            title = "Numbers",
            subtitle = "People, context, and reply controls",
            trailing = {
                TextButton(onClick = onImportContacts, enabled = !busy) {
                    Icon(Icons.Outlined.ImportContacts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }
        )
        if (contacts.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
        }
        if (shownContacts.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.ImportContacts,
                title = "Build your number book",
                detail = "Add a number and give it a category, profession, and purpose. Auto-reply stays off until you opt in.",
                actionLabel = "Add a number",
                onAction = onAdd
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shownContacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        onOpen = { onOpen(contact.phone) },
                        onEdit = { onEdit(contact) },
                        onWhatsApp = { onWhatsApp(contact.phone) },
                        onToggleAutoReply = { enabled -> onToggleAutoReply(contact.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
fun LocalAiScreen(
    modifier: Modifier = Modifier,
    trainingCount: Int,
    autoReplyEnabled: Boolean,
    busy: Boolean,
    suggestion: ReplySuggestion?,
    onImport: (Uri) -> Unit,
    onPreview: (String) -> Unit,
    onAutoReplyChanged: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    var testMessage by rememberSaveable { mutableStateOf("") }
    var submittedPreview by rememberSaveable { mutableStateOf("") }
    var previewStarted by rememberSaveable { mutableStateOf(false) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeading(title = "Local AI", subtitle = "Small, private, and trained on your examples")

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("On-device learning", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
                Text(
                    "$trainingCount examples saved locally",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "TF-IDF retrieval + Naive Bayes intent matching. Relay only reuses a reply you supplied; weak matches are ignored.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("No cloud model or internet permission.", fontWeight = FontWeight.Medium)
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add local training data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Import JSON or a SQLite database with message examples and their desired replies.")
                Button(
                    onClick = {
                        fileLauncher.launch(
                            arrayOf(
                                "application/json",
                                "application/vnd.sqlite3",
                                "application/x-sqlite3",
                                "application/octet-stream"
                            )
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "Importing…" else "Choose JSON or SQLite file")
                }
                TextButton(onClick = onClear, enabled = !busy && trainingCount > 0) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Clear training data")
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Accepted file format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("JSON: version 1 with examples containing intent, input, and reply fields.")
                Text("SQLite: examples or training_examples table with intent, input_text, and reply_text columns.")
                Text("Up to 5,000 rows per import and 10,000 saved total. See docs/LOCAL_AI_TRAINING.md for examples and limits.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Try a suggestion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = testMessage,
                    onValueChange = { testMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Example incoming message") },
                    minLines = 2,
                    maxLines = 4
                )
                OutlinedButton(
                    onClick = {
                        submittedPreview = testMessage
                        previewStarted = true
                        onPreview(testMessage)
                    },
                    enabled = !busy && testMessage.isNotBlank() && trainingCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Find the closest learned reply")
                }
                if (suggestion != null && testMessage == submittedPreview) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${suggestion.intent} · ${String.format(Locale.ROOT, "%.0f", suggestion.confidence * 100)}% match", fontWeight = FontWeight.SemiBold)
                            Text(suggestion.reply)
                            Text("Closest example: “${suggestion.matchedInput}”", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (previewStarted && testMessage == submittedPreview && testMessage.isNotBlank() && trainingCount > 0) {
                    Text("No strong match yet. Add more examples rather than relying on a guess.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic replies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (autoReplyEnabled) "Enabled globally" else "Off by default", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoReplyEnabled, onCheckedChange = onAutoReplyChanged)
                }
                HorizontalDivider()
                Text("You must also enable replies for individual numbers. Relay only responds to a strong match, ignores opt-out words, and applies a one-day per-number cooldown.")
                Text("Test suggestions before turning this on. Replies are not suitable for emergencies or high-stakes conversations.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    refreshVersion: Int,
    roleLauncher: (Intent) -> Unit,
    permissionLauncher: (Array<String>) -> Unit,
    onRefresh: () -> Unit,
    onNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val isDefault = remember(refreshVersion) { SmsRole.isDefault(context) }
    val missing = remember(refreshVersion) { SmsRole.missingRuntimePermissions(context) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeading(title = "Settings", subtitle = "You stay in control of SMS and local data")

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Default SMS app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (isDefault) "Relay is selected" else "Required to send and receive SMS")
                    }
                }
                Button(
                    onClick = {
                        val request = SmsRole.requestIntent(context)
                        if (request == null) onNotice("This device does not support the Android SMS role.") else roleLauncher(request)
                    },
                    enabled = !isDefault,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isDefault) "Default SMS app set" else "Choose Relay as default")
                }
                Text("Android shows its own system confirmation. Relay never changes the default app silently.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (missing.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Requested SMS and notification permissions are granted.")
                    }
                } else {
                    Text("Android will request only the listed permissions when you continue:")
                    missing.forEach { permission ->
                        Text("• ${permissionLabel(permission)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = { permissionLauncher(missing.toTypedArray()) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Review permissions") }
                }
                Text("Android controls SMS-provider write access through the default-SMS role. No call-log, microphone, camera, location, or internet permission is requested.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Private by design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Contacts, imported examples, and Relay's message index are stored in app-private SQLite. Nothing is uploaded, and app backup is disabled.")
                Text("WhatsApp opens through its official wa.me link. Relay cannot read or sync WhatsApp chats.")
                Text("Android still maintains its system SMS database while Relay is the default handler.", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh permission status") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ConversationScreen(
    modifier: Modifier = Modifier,
    phone: String,
    contact: ContactRecord?,
    messages: List<SmsRecord>,
    sending: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Boolean,
    onWhatsApp: (String) -> Unit
) {
    var draft by rememberSaveable(phone) { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(contact?.name ?: phone, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (contact != null) Text(phone, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onWhatsApp(phone) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open official WhatsApp chat link")
            }
        }
        HorizontalDivider()
        if (contact != null && (contact.category.isNotBlank() || contact.purpose.isNotBlank())) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    listOf(contact.category, contact.profession, contact.purpose).filter { it.isNotBlank() }.joinToString(" · "),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Start the conversation with $phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message -> MessageBubble(message) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 1_000) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Text message") },
                maxLines = 4
            )
            IconButton(
                onClick = {
                    val outgoing = draft.trim()
                    if (outgoing.isNotEmpty() && onSend(outgoing)) {
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank() && !sending
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send SMS", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ThreadCard(thread: ThreadRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(thread.displayName)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(thread.displayName, modifier = Modifier.weight(1f), fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatTime(thread.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    (if (thread.lastMessageOutgoing) "You: " else "") + thread.preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (thread.unreadCount > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Text(thread.unreadCount.coerceAtMost(99).toString(), color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: ContactRecord,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onWhatsApp: () -> Unit,
    onToggleAutoReply: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(contact.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                    Text(contact.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit ${contact.name}") }
            }
            if (contact.category.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text(contact.category) }, leadingIcon = null)
            }
            if (contact.profession.isNotBlank()) Text("Profession · ${contact.profession}", style = MaterialTheme.typography.bodySmall)
            if (contact.purpose.isNotBlank()) Text("Purpose · ${contact.purpose}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow local auto-reply", fontWeight = FontWeight.Medium)
                    Text("Only when the global switch is on", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = contact.autoReplyEnabled, onCheckedChange = onToggleAutoReply)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("SMS")
                }
                OutlinedButton(onClick = onWhatsApp, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SmsRecord) {
    val outgoing = message.direction == com.relaymessages.data.MessageDirection.OUTGOING
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = if (outgoing) 5.dp else 20.dp,
                bottomStart = if (outgoing) 20.dp else 5.dp
            ),
            color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message.body)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (message.isAutoReply) Text("Local auto-reply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InitialAvatar(label: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ScreenHeading(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun ComposeRecipientDialog(
    contacts: List<ContactRecord>,
    onDismiss: () -> Unit,
    onContinue: (String) -> Unit
) {
    var phone by rememberSaveable { mutableStateOf("") }
    val matches = contacts.filter { phone.isNotBlank() && it.name.contains(phone, ignoreCase = true) }.take(5)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Phone number") },
                    placeholder = { Text("Include country code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                if (matches.isNotEmpty()) {
                    Text("Matching saved numbers", style = MaterialTheme.typography.labelMedium)
                    matches.forEach { contact ->
                        TextButton(onClick = { phone = contact.phone }, modifier = Modifier.fillMaxWidth()) {
                            Text("${contact.name} · ${contact.phone}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Text("WhatsApp is opened separately from the SMS conversation.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (phone.filter { it.isDigit() }.length >= 5) onContinue(phone.trim()) },
                enabled = phone.filter { it.isDigit() }.length >= 5
            ) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ContactEditorDialog(
    initial: ContactRecord?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (ContactRecord) -> Unit
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by rememberSaveable(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.category.orEmpty()) }
    var profession by rememberSaveable(initial?.id) { mutableStateOf(initial?.profession.orEmpty()) }
    var purpose by rememberSaveable(initial?.id) { mutableStateOf(initial?.purpose.orEmpty()) }
    var autoReply by rememberSaveable(initial?.id) { mutableStateOf(initial?.autoReplyEnabled ?: false) }
    val suggestions = listOf("Family", "Friends", "Work", "Services", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a number" else "Edit number") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    phone,
                    { phone = it.take(32) },
                    label = { Text("Phone number") },
                    placeholder = { Text("Include country code") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                OutlinedTextField(category, { category = it.take(64) }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions) { suggestion ->
                        FilterChip(selected = category == suggestion, onClick = { category = suggestion }, label = { Text(suggestion) })
                    }
                }
                OutlinedTextField(profession, { profession = it.take(80) }, label = { Text("Profession") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    purpose,
                    { purpose = it.take(240) },
                    label = { Text("Purpose / notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow auto-reply for this number", fontWeight = FontWeight.Medium)
                        Text("Global auto-reply must also be on", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoReply, onCheckedChange = { autoReply = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ContactRecord(
                        id = initial?.id ?: 0,
                        name = name.trim(),
                        phone = phone.trim(),
                        category = category.trim(),
                        profession = profession.trim(),
                        purpose = purpose.trim(),
                        autoReplyEnabled = autoReply,
                        lastAutoReplyAt = initial?.lastAutoReplyAt ?: 0
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun formatTime(timestamp: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.SEND_SMS -> "Send SMS"
    Manifest.permission.RECEIVE_SMS -> "Receive SMS"
    Manifest.permission.READ_SMS -> "Read SMS history"
    Manifest.permission.RECEIVE_MMS -> "Receive MMS delivery events"
    Manifest.permission.RECEIVE_WAP_PUSH -> "Receive MMS push events"
    Manifest.permission.READ_CONTACTS -> "Read contacts when you choose to import them"
    Manifest.permission.POST_NOTIFICATIONS -> "Show message notifications"
    else -> permission.substringAfterLast('.')
}
