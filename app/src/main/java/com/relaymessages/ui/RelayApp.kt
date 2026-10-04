package com.relaymessages.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relaymessages.data.ContactRecord

private enum class AppTab(val title: String) {
    CHATS("Chats"),
    NUMBERS("Numbers"),
    LEARN("Local AI"),
    SETTINGS("Settings")
}

@Composable
fun RelayApp(
    initialPhone: String? = null,
    messagesViewModel: MessagesViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.CHATS) }
    var activePhone by rememberSaveable { mutableStateOf<String?>(null) }
    var recipientDialog by rememberSaveable { mutableStateOf(false) }
    var contactDialogOpen by rememberSaveable { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ContactRecord?>(null) }
    var clearTrainingDialog by rememberSaveable { mutableStateOf(false) }
    var importingDeviceContacts by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        messagesViewModel.refresh()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (importingDeviceContacts && results[android.Manifest.permission.READ_CONTACTS] == true) {
            messagesViewModel.importDeviceContacts()
        }
        importingDeviceContacts = false
        messagesViewModel.refresh()
    }

    LaunchedEffect(Unit) {
        messagesViewModel.refresh()
    }
    LaunchedEffect(initialPhone) {
        if (!initialPhone.isNullOrBlank()) {
            activePhone = initialPhone
            messagesViewModel.openConversation(initialPhone)
        }
    }
    LaunchedEffect(messagesViewModel.notice) {
        val notice = messagesViewModel.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice, duration = SnackbarDuration.Short)
        messagesViewModel.dismissNotice()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (activePhone == null) {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            AppTab.CHATS -> Icons.Outlined.ChatBubbleOutline
                            AppTab.NUMBERS -> Icons.Outlined.Contacts
                            AppTab.LEARN -> Icons.Outlined.AutoAwesome
                            AppTab.SETTINGS -> Icons.Outlined.Settings
                        }
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (activePhone == null) {
                when (selectedTab) {
                    AppTab.CHATS -> FloatingActionButton(onClick = { recipientDialog = true }) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "New message")
                    }
                    AppTab.NUMBERS -> FloatingActionButton(onClick = {
                        editingContact = null
                        contactDialogOpen = true
                    }) {
                        Icon(Icons.Outlined.Contacts, contentDescription = "Add number")
                    }
                    else -> Unit
                }
            }
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) { padding ->
        if (activePhone != null) {
            ConversationScreen(
                modifier = Modifier.padding(padding),
                phone = activePhone!!,
                contact = messagesViewModel.contacts.firstOrNull {
                    com.relaymessages.data.AppDatabase.normalizePhone(it.phone) ==
                        com.relaymessages.data.AppDatabase.normalizePhone(activePhone!!)
                },
                messages = messagesViewModel.activeMessages,
                sending = messagesViewModel.busy,
                onBack = {
                    activePhone = null
                    messagesViewModel.refresh()
                },
                onSend = { body -> messagesViewModel.sendMessage(activePhone!!, body) },
                onWhatsApp = { phone -> openWhatsApp(context, phone) }
            )
        } else {
            when (selectedTab) {
                AppTab.CHATS -> ChatsScreen(
                    modifier = Modifier.padding(padding),
                    threads = messagesViewModel.threads,
                    onOpen = { phone ->
                        activePhone = phone
                        messagesViewModel.openConversation(phone)
                    },
                    onCompose = { recipientDialog = true },
                    onGoToSettings = { selectedTab = AppTab.SETTINGS }
                )
                AppTab.NUMBERS -> NumbersScreen(
                    modifier = Modifier.padding(padding),
                    contacts = messagesViewModel.contacts,
                    onAdd = {
                        editingContact = null
                        contactDialogOpen = true
                    },
                    onEdit = { contact ->
                        editingContact = contact
                        contactDialogOpen = true
                    },
                    onOpen = { phone ->
                        activePhone = phone
                        messagesViewModel.openConversation(phone)
                    },
                    onWhatsApp = { phone -> openWhatsApp(context, phone) },
                    onToggleAutoReply = messagesViewModel::setContactAutoReply,
                    onImportContacts = {
                        val permission = android.Manifest.permission.READ_CONTACTS
                        if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            messagesViewModel.importDeviceContacts()
                        } else {
                            importingDeviceContacts = true
                            permissionLauncher.launch(arrayOf(permission))
                        }
                    },
                    busy = messagesViewModel.busy
                )
                AppTab.LEARN -> LocalAiScreen(
                    modifier = Modifier.padding(padding),
                    trainingCount = messagesViewModel.trainingCount,
                    autoReplyEnabled = messagesViewModel.autoReplyEnabled,
                    busy = messagesViewModel.busy,
                    suggestion = messagesViewModel.previewSuggestion,
                    onImport = messagesViewModel::importTraining,
                    onPreview = messagesViewModel::previewReply,
                    onAutoReplyChanged = messagesViewModel::setGlobalAutoReply,
                    onClear = { clearTrainingDialog = true }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    refreshVersion = messagesViewModel.refreshVersion,
                    roleLauncher = { intent -> roleLauncher.launch(intent) },
                    permissionLauncher = { permissions -> permissionLauncher.launch(permissions) },
                    onRefresh = messagesViewModel::refresh,
                    onNotice = messagesViewModel::showNotice
                )
            }
        }
    }

    if (recipientDialog) {
        ComposeRecipientDialog(
            contacts = messagesViewModel.contacts,
            onDismiss = { recipientDialog = false },
            onContinue = { phone ->
                recipientDialog = false
                activePhone = phone
                messagesViewModel.openConversation(phone)
            }
        )
    }

    if (contactDialogOpen) {
        ContactEditorDialog(
            initial = editingContact,
            onDismiss = { contactDialogOpen = false },
            onDelete = editingContact?.let { contact ->
                {
                    messagesViewModel.deleteContact(contact.id)
                    contactDialogOpen = false
                }
            },
            onSave = { contact ->
                if (messagesViewModel.saveContact(contact)) contactDialogOpen = false
            }
        )
    }

    if (clearTrainingDialog) {
        AlertDialog(
            onDismissRequest = { clearTrainingDialog = false },
            title = { Text("Clear local training data?") },
            text = { Text("This removes imported examples from Relay and turns auto-reply off. It does not delete SMS from Android's system inbox.") },
            confirmButton = {
                TextButton(onClick = {
                    messagesViewModel.clearTraining()
                    clearTrainingDialog = false
                }) { Text("Clear examples") }
            },
            dismissButton = { TextButton(onClick = { clearTrainingDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun openWhatsApp(context: android.content.Context, phone: String) {
    val digits = phone.filter { it.isDigit() }
    if (digits.length < 5) return
    val uri = Uri.parse("https://wa.me/$digits")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
