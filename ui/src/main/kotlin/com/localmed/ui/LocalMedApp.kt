package com.localmed.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localmed.ai.model.ModelValidationState
import com.localmed.conversation.api.OutgoingMessage
import com.localmed.ai.api.ResponseState
import com.localmed.knowledge.api.KnowledgeRecord
import com.localmed.knowledge.api.ReviewStatus

private enum class AppTab(val title: String, val glyph: String) {
    ASK("Ask", "A"), LIBRARY("Library", "L"), MODELS("Models", "M"), RESEARCH("Research", "R"), SETTINGS("Settings", "S")
}

@Composable
fun LocalMedApp(
    state: LocalMedUiState,
    onSubmitQuestion: (String) -> Unit,
    onImportKnowledge: (Uri) -> Unit,
    onImportModel: (Uri, publisherKeyId: String) -> Unit,
    onImportTrustedPublisher: (Uri, keyId: String, displayName: String, fingerprint: String) -> Unit,
    onImportTrainingDataset: (Uri, displayName: String, source: String, license: String) -> Unit,
    onReviewKnowledge: (String, ReviewStatus, String) -> Unit,
    onDeleteKnowledge: (String) -> Unit,
    onValidateModel: (String, String) -> Unit,
    onActivateModel: (String, String) -> Unit,
    onRemovePublisher: (String) -> Unit,
    onDeleteTrainingDataset: (String) -> Unit,
    onSearchPubMed: (String) -> Unit,
    onSetWebSearchEnabled: (Boolean) -> Unit,
    onCreateSmsDraft: (String, String) -> Unit,
    onWhatsAppHandoff: (String, String) -> Unit,
    onDismissNotice: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingModelPublisher by rememberSaveable { mutableStateOf("") }
    var pendingKeyInfo by remember { mutableStateOf<PendingKeyInfo?>(null) }
    var pendingDatasetInfo by remember { mutableStateOf<PendingDatasetInfo?>(null) }
    var pendingShareText by remember { mutableStateOf<String?>(null) }
    var showTrustDialog by rememberSaveable { mutableStateOf(false) }
    var showDatasetDialog by rememberSaveable { mutableStateOf(false) }

    val knowledgePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportKnowledge(uri)
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingModelPublisher.isNotBlank()) onImportModel(uri, pendingModelPublisher)
        pendingModelPublisher = ""
    }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val keyInfo = pendingKeyInfo
        if (uri != null && keyInfo != null) {
            onImportTrustedPublisher(uri, keyInfo.keyId, keyInfo.displayName, keyInfo.fingerprint)
        }
        pendingKeyInfo = null
    }
    val datasetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val info = pendingDatasetInfo
        if (uri != null && info != null) onImportTrainingDataset(uri, info.displayName, info.source, info.license)
        pendingDatasetInfo = null
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(tab.glyph, fontWeight = FontWeight.Bold) },
                        label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LocalMed Research", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Offline-first · source-backed · private by default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.isBusy) CircularProgressIndicator(modifier = Modifier.width(26.dp).height(26.dp), strokeWidth = 2.dp)
            }

            if (state.notice != null) {
                NoticeCard(state.notice, onDismissNotice)
            }

            when (AppTab.entries.getOrElse(selectedTab) { AppTab.ASK }) {
                AppTab.ASK -> AskScreen(
                    state = state,
                    onSubmitQuestion = onSubmitQuestion,
                    onShare = { pendingShareText = it }
                )
                AppTab.LIBRARY -> LibraryScreen(
                    state = state,
                    onOpenImporter = { knowledgePicker.launch(arrayOf("application/x-ndjson", "application/json", "text/plain", "*/*")) },
                    onReview = onReviewKnowledge,
                    onDelete = onDeleteKnowledge
                )
                AppTab.MODELS -> ModelScreen(
                    state = state,
                    onAddPublisher = { showTrustDialog = true },
                    onImportModel = { keyId ->
                        if (keyId.isBlank()) {
                            // A bundle has to be signed by a key the user verified and trusted first.
                            selectedTab = AppTab.MODELS.ordinal
                        } else {
                            pendingModelPublisher = keyId
                            modelPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }
                    },
                    onValidateModel = onValidateModel,
                    onActivateModel = onActivateModel,
                    onRemovePublisher = onRemovePublisher,
                    onImportDataset = { showDatasetDialog = true },
                    onDeleteDataset = onDeleteTrainingDataset
                )
                AppTab.RESEARCH -> ResearchScreen(
                    state = state,
                    onSearch = onSearchPubMed
                )
                AppTab.SETTINGS -> SettingsScreen(
                    state = state,
                    onSetWebSearchEnabled = onSetWebSearchEnabled
                )
            }
        }
    }

    if (showTrustDialog) {
        TrustPublisherDialog(
            onDismiss = { showTrustDialog = false },
            onChooseKeyFile = { info ->
                pendingKeyInfo = info
                showTrustDialog = false
                keyPicker.launch(arrayOf("application/x-pem-file", "application/octet-stream", "text/plain", "*/*"))
            }
        )
    }

    if (showDatasetDialog) {
        DatasetConsentDialog(
            onDismiss = { showDatasetDialog = false },
            onChooseFile = { info ->
                pendingDatasetInfo = info
                showDatasetDialog = false
                datasetPicker.launch(arrayOf("application/x-ndjson", "application/json", "text/plain", "*/*"))
            }
        )
    }

    pendingShareText?.let { shareText ->
        ShareDraftDialog(
            text = shareText,
            onDismiss = { pendingShareText = null },
            onSms = { phone, body ->
                pendingShareText = null
                onCreateSmsDraft(phone, body)
            },
            onWhatsApp = { phone, body ->
                pendingShareText = null
                onWhatsAppHandoff(phone, body)
            }
        )
    }
}

@Composable
private fun AskScreen(
    state: LocalMedUiState,
    onSubmitQuestion: (String) -> Unit,
    onShare: (String) -> Unit
) {
    var question by remember { mutableStateOf("") }
    SectionTitle("Ask locally", "No conversation is saved. Only reviewed local evidence is eligible for retrieval.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill("LOCAL CORE", MaterialTheme.colorScheme.primaryContainer)
        StatusPill(if (state.loadedModelLabel == null) "NO MODEL" else "MODEL READY", MaterialTheme.colorScheme.secondaryContainer)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.deviceStatus, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Inference and knowledge search stay on this device. Imported evidence starts unverified and is never used until you review it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    OutlinedTextField(
        value = question,
        onValueChange = { question = it.take(4_000) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Educational or research question") },
        supportingText = { Text("Avoid patient-identifying information. The app does not persist this conversation.") },
        minLines = 3,
        maxLines = 6,
        enabled = !state.isBusy
    )
    Button(
        onClick = { onSubmitQuestion(question.trim()) },
        enabled = question.isNotBlank() && !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Search reviewed local sources") }

    state.response?.let { response -> ResponseCard(response, onShare) }

    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Safety boundary", fontWeight = FontWeight.SemiBold)
            Text("Not for diagnosis, prescribing, dosage decisions, or urgent care. Emergency and high-risk requests are intercepted by deterministic rules before local model inference. These rules are not clinically validated.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResponseCard(response: OutgoingMessage, onShare: (String) -> Unit) {
    val label = when (response.responseState) {
        ResponseState.EMERGENCY -> "Urgent safety redirection"
        ResponseState.PATIENT_SPECIFIC_HIGH_RISK -> "High-risk request redirected"
        ResponseState.PATIENT_SPECIFIC_LOW_RISK -> "Patient-specific question"
        ResponseState.INSUFFICIENT_INFORMATION -> "Insufficient local evidence"
        ResponseState.UNCERTAIN -> "Uncertain / not generated"
        ResponseState.REFUSAL_OR_REDIRECTION -> "Redirected"
        ResponseState.EDUCATIONAL -> "Educational information"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (response.generatedByModel) {
                Text("Generated by an imported local model · not clinically validated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text("No model-generated answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SelectionContainer { Text(response.content, style = MaterialTheme.typography.bodyLarge) }
            if (response.modelVersion != null) Text("Model version: ${response.modelVersion}", style = MaterialTheme.typography.labelSmall)
            response.citations.forEach { citation ->
                HorizontalDivider()
                Text(citation.title, fontWeight = FontWeight.SemiBold)
                Text("${citation.source} · ${citation.evidenceLevel}${citation.publicationYear?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                val dateMetadata = listOfNotNull(
                    citation.effectiveDate?.let { "Effective: $it" },
                    citation.reviewOrExpirationDate?.let { "Review/expiry: $it" }
                ).joinToString(" · ")
                if (dateMetadata.isNotBlank()) Text(dateMetadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                SelectionContainer { Text(citation.excerpt, style = MaterialTheme.typography.bodySmall) }
                Text("Source ID: ${citation.recordId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (response.responseState == ResponseState.EDUCATIONAL && response.content.isNotBlank()) {
                OutlinedButton(onClick = { onShare(response.content) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Review and prepare a share handoff")
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: LocalMedUiState,
    onOpenImporter: () -> Unit,
    onReview: (String, ReviewStatus, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("") }
    var recordAwaitingVerification by remember { mutableStateOf<KnowledgeRecord?>(null) }
    SectionTitle("Knowledge library", "Verified local records only are used by the offline retrieval path.")
    Text("${state.knowledgeRecords.count { it.reviewStatus == ReviewStatus.VERIFIED }} verified · ${state.knowledgeRecords.count { it.reviewStatus == ReviewStatus.UNREVIEWED }} need review", style = MaterialTheme.typography.labelLarge)
    Button(onClick = onOpenImporter, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy) { Text("Import licensed knowledge JSONL") }
    OutlinedTextField(value = filter, onValueChange = { filter = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Filter records") }, singleLine = true)
    if (state.knowledgeRecords.isEmpty()) {
        EmptyCard("No knowledge records are installed. Import a licensed, provenance-bearing JSONL file; no medical corpus is bundled.")
    }
    state.knowledgeRecords.filter { record ->
        filter.isBlank() || listOf(record.title, record.specialty, record.source, record.id).any { it.contains(filter, ignoreCase = true) }
    }.take(100).forEach { record ->
        KnowledgeRecordCard(
            record = record,
            onReview = { recordAwaitingVerification = record },
            onReject = { onReview(record.id, ReviewStatus.REJECTED, "Rejected during local source review.") },
            onDelete = { onDelete(record.id) }
        )
    }
    recordAwaitingVerification?.let { record ->
        AlertDialog(
            onDismissRequest = { recordAwaitingVerification = null },
            title = { Text("Verify this source?") },
            text = { Text("Mark “${record.title}” as verified only after you check the original source, license, publication date, and clinical context. Verification is your local review, not an independent clinical appraisal.") },
            confirmButton = {
                TextButton(onClick = {
                    onReview(record.id, ReviewStatus.VERIFIED, "Source and license reviewed locally by the user.")
                    recordAwaitingVerification = null
                }) { Text("I reviewed the source") }
            },
            dismissButton = { TextButton(onClick = { recordAwaitingVerification = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun KnowledgeRecordCard(
    record: KnowledgeRecord,
    onReview: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                StatusPill(record.reviewStatus.name, when (record.reviewStatus) {
                    ReviewStatus.VERIFIED -> MaterialTheme.colorScheme.primaryContainer
                    ReviewStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
                    ReviewStatus.UNREVIEWED -> MaterialTheme.colorScheme.tertiaryContainer
                })
            }
            Text("${record.specialty} · ${record.recordType.name} · ${record.evidenceLevel.name}", style = MaterialTheme.typography.labelSmall)
            Text("${record.source} · ${record.authorOrOrganization}${record.publicationYear?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            Text("License: ${record.license}", style = MaterialTheme.typography.bodySmall)
            SelectionContainer { Text(record.content.take(600) + if (record.content.length > 600) "…" else "", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.reviewStatus != ReviewStatus.VERIFIED) OutlinedButton(onClick = onReview) { Text("Review") }
                if (record.reviewStatus == ReviewStatus.UNREVIEWED) TextButton(onClick = onReject) { Text("Reject") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ModelScreen(
    state: LocalMedUiState,
    onAddPublisher: () -> Unit,
    onImportModel: (String) -> Unit,
    onValidateModel: (String, String) -> Unit,
    onActivateModel: (String, String) -> Unit,
    onRemovePublisher: (String) -> Unit,
    onImportDataset: () -> Unit,
    onDeleteDataset: (String) -> Unit
) {
    SectionTitle("Models and training data", "ONNX weights are not bundled. Imports require integrity checks and a publisher key you trust.")
    if (state.loadedModelLabel == null) {
        EmptyCard("No model is loaded. Core search stays offline; without an imported compatible model, the app will show source excerpts only.")
    } else {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Loaded local model", fontWeight = FontWeight.SemiBold)
                Text(state.loadedModelLabel!!)
                Text("Model authenticity and runtime compatibility do not establish clinical validity.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("Trusted publisher keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text("Compare each SHA-256 fingerprint through an independent channel before granting trust.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = onAddPublisher, modifier = Modifier.fillMaxWidth()) { Text("Trust a publisher key") }
    state.trustedPublishers.forEach { publisher ->
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(publisher.displayName, fontWeight = FontWeight.SemiBold)
                Text("Key ID: ${publisher.keyId}", style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text("SHA-256: ${publisher.fingerprint}", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { onRemovePublisher(publisher.keyId) }) { Text("Remove trust") }
            }
        }
    }
    if (state.trustedPublishers.isEmpty()) EmptyCard("No publisher is trusted. Importing a model is disabled until you verify and add a public key.")
    Text("Installed model bundles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    state.models.forEach { model ->
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${model.id} · ${model.version}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    StatusPill(if (model.isActive) "ACTIVE" else model.status.name, if (model.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                }
                Text("${model.architecture} · ${model.source} · ${model.license}", style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text("ONNX SHA-256: ${model.sha256}\nTokenizer SHA-256: ${model.tokenizerSha256}", style = MaterialTheme.typography.labelSmall) }
                when (model.status) {
                    ModelValidationState.IMPORTED -> OutlinedButton(onClick = { onValidateModel(model.id, model.version) }, enabled = !state.isBusy) { Text("Validate and smoke-test") }
                    ModelValidationState.SMOKE_TESTED -> Button(onClick = { onActivateModel(model.id, model.version) }, enabled = !state.isBusy) { Text("Activate this model") }
                    else -> Unit
                }
            }
        }
    }
    if (state.trustedPublishers.isNotEmpty()) {
        Text("Import signed model bundle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        state.trustedPublishers.forEach { publisher ->
            OutlinedButton(onClick = { onImportModel(publisher.keyId) }, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy) {
                Text("Choose bundle signed by ${publisher.displayName}")
            }
        }
    }
    HorizontalDivider()
    Text("Training datasets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text("This build validates and encrypts explicit dataset imports, but contains no training backend. No training or simulated success will be reported.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = onImportDataset, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy) { Text("Import a licensed training JSONL dataset") }
    state.trainingDatasets.forEach { dataset ->
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dataset.displayName, fontWeight = FontWeight.SemiBold)
                Text("${dataset.examples} validated examples · ${dataset.source} · ${dataset.license}", style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text("SHA-256: ${dataset.sha256}", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { onDeleteDataset(dataset.id) }) { Text("Delete encrypted dataset") }
            }
        }
    }
}

@Composable
private fun ResearchScreen(state: LocalMedUiState, onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var pendingQuery by remember { mutableStateOf<String?>(null) }
    SectionTitle("Optional PubMed search", "Separate from the offline assistant. Each request needs explicit confirmation.")
    if (!state.webSearchBuildAvailable) {
        EmptyCard("This is the offline build. It contains no INTERNET permission; web search is unavailable.")
    } else if (!state.webSearchEnabled) {
        EmptyCard("PubMed search is off. Enable it in Settings before making any network request.")
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.take(500) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("PubMed search terms") },
        supportingText = { Text("Only the query is sent to NCBI after confirmation. Do not enter identifying patient data.") },
        minLines = 2,
        maxLines = 4,
        enabled = state.webSearchBuildAvailable && state.webSearchEnabled && !state.isBusy
    )
    Button(
        onClick = { pendingQuery = query.trim() },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.webSearchBuildAvailable && state.webSearchEnabled && query.trim().length >= 3 && !state.isBusy
    ) { Text("Review and confirm search") }
    if (state.researchQueryHash != null) Text("Query hash: ${state.researchQueryHash}", style = MaterialTheme.typography.labelSmall)
    Text("Network results are untrusted reference material. They never override the deterministic safety policy or enter local model context automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    state.researchResults.forEach { article ->
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(article.title, fontWeight = FontWeight.SemiBold)
                Text("${article.journal} · ${article.publicationDate} · PMID ${article.pmid}", style = MaterialTheme.typography.labelSmall)
                if (article.abstractText.isNotBlank()) SelectionContainer { Text(article.abstractText, style = MaterialTheme.typography.bodySmall) }
                Text(article.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text("Content SHA-256: ${article.sha256}", style = MaterialTheme.typography.labelSmall) }
                Text("Untrusted external source · verify the original publication.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    pendingQuery?.let { confirmedQuery ->
        AlertDialog(
            onDismissRequest = { pendingQuery = null },
            title = { Text("Send this query to PubMed?") },
            text = { Text("This makes an Internet request to NCBI. Confirm that the query contains no patient-identifying information. The returned abstracts are untrusted and will not be used as local model evidence.") },
            confirmButton = {
                TextButton(onClick = { onSearch(confirmedQuery); pendingQuery = null }) { Text("Send one search") }
            },
            dismissButton = { TextButton(onClick = { pendingQuery = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsScreen(state: LocalMedUiState, onSetWebSearchEnabled: (Boolean) -> Unit) {
    SectionTitle("Privacy and permissions", "Local by default. Optional capabilities are separate and visible.")
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Conversation privacy", fontWeight = FontWeight.SemiBold)
            Text("Conversation turns are processed in memory and are not persisted. Long-term memory and conversation training are not enabled.", style = MaterialTheme.typography.bodySmall)
            Text("Do not enter patient names, phone numbers, medical record numbers, or other identifying data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Optional PubMed network access", fontWeight = FontWeight.SemiBold)
            if (!state.webSearchBuildAvailable) {
                Text("Unavailable in the offline build. No Internet permission is declared.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable explicit research search", modifier = Modifier.weight(1f))
                    Switch(checked = state.webSearchEnabled, onCheckedChange = onSetWebSearchEnabled)
                }
                Text("Every query still requires a per-search confirmation. Disabling this revokes tool authorization.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Device capability", fontWeight = FontWeight.SemiBold)
            Text(state.deviceStatus, style = MaterialTheme.typography.bodySmall)
            Text("SMS opens a user-reviewed draft only. WhatsApp uses an official click-to-chat handoff only. Neither integration reads private conversations or sends automatically.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Medical safety", fontWeight = FontWeight.SemiBold)
            Text("This is not a medical device. Safety checks are deterministic but not clinically validated. Do not use this app for emergency care, diagnosis, prescribing, or medication dosing.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NoticeCard(message: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

private data class PendingKeyInfo(val keyId: String, val displayName: String, val fingerprint: String)
private data class PendingDatasetInfo(val displayName: String, val source: String, val license: String)

@Composable
private fun TrustPublisherDialog(onDismiss: () -> Unit, onChooseKeyFile: (PendingKeyInfo) -> Unit) {
    var keyId by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trust publisher key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Obtain an RSA public key from a trusted source. Verify its SHA-256 fingerprint out-of-band; this app cannot establish the publisher's identity for you.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(keyId, { keyId = it.take(128) }, label = { Text("Publisher key ID") }, singleLine = true)
                OutlinedTextField(displayName, { displayName = it.take(120) }, label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(fingerprint, { fingerprint = it.take(128) }, label = { Text("Verified SHA-256 fingerprint") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChooseKeyFile(PendingKeyInfo(keyId.trim(), displayName.trim(), fingerprint.trim())) },
                enabled = keyId.isNotBlank() && displayName.isNotBlank() && fingerprint.filter(Char::isLetterOrDigit).length == 64
            ) { Text("Choose key file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DatasetConsentDialog(onDismiss: () -> Unit, onChooseFile: (PendingDatasetInfo) -> Unit) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var license by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import training data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Explicit user-selected data only. The JSONL is validated and encrypted on-device. This build has no training backend and will not read conversation history or report a simulated training result.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(displayName, { displayName = it.take(200) }, label = { Text("Dataset name") }, singleLine = true)
                OutlinedTextField(source, { source = it.take(500) }, label = { Text("Source / provenance") }, singleLine = true)
                OutlinedTextField(license, { license = it.take(200) }, label = { Text("License / usage rights") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChooseFile(PendingDatasetInfo(displayName.trim(), source.trim(), license.trim())) },
                enabled = displayName.isNotBlank() && source.isNotBlank() && license.isNotBlank()
            ) { Text("I have rights to use this data") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ShareDraftDialog(
    text: String,
    onDismiss: () -> Unit,
    onSms: (String, String) -> Unit,
    onWhatsApp: (String, String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var body by remember(text) { mutableStateOf(text.take(1_000)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review a handoff") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nothing is sent by this app. SMS opens a draft in the system messaging app; WhatsApp opens its official click-to-chat handoff. Review the recipient and every word before sending.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(phone, { phone = it.take(32) }, label = { Text("Recipient phone number") }, singleLine = true)
                OutlinedTextField(body, { body = it.take(1_000) }, label = { Text("Message to review") }, minLines = 3, maxLines = 7)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onSms(phone.trim(), body.trim()) }, enabled = phone.filter(Char::isDigit).length >= 5 && body.isNotBlank()) { Text("Open SMS draft") }
                TextButton(onClick = { onWhatsApp(phone.trim(), body.trim()) }, enabled = phone.filter(Char::isDigit).length >= 5 && body.isNotBlank()) { Text("Open WhatsApp handoff") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
