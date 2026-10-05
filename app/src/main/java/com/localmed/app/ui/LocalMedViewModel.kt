package com.localmed.app.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmed.ai.api.GenerationRequest
import com.localmed.ai.api.InferenceEngine
import com.localmed.ai.api.LoadResult
import com.localmed.ai.model.ModelManifest
import com.localmed.ai.model.ModelManifestValidator
import com.localmed.ai.model.ModelRegistry
import com.localmed.ai.model.ModelRegistryEntry
import com.localmed.ai.model.ModelSignatureVerifier
import com.localmed.ai.model.ModelValidationState
import com.localmed.ai.model.TrustedPublisherRepository
import com.localmed.ai.tokenizer.RustTokenizerFactory
import com.localmed.ai.training.TrainingDatasetRepository
import com.localmed.app.BuildConfig
import com.localmed.app.data.AppPreferencesStore
import com.localmed.conversation.api.ConversationGateway
import com.localmed.conversation.api.NormalizedMessage
import com.localmed.conversation.api.OutgoingMessage
import com.localmed.conversation.api.ResponseState
import com.localmed.core.logging.LogSeverity
import com.localmed.core.logging.StructuredLogEvent
import com.localmed.core.logging.StructuredLogger
import com.localmed.core.security.EncryptedArtifactStore
import com.localmed.integration.sms.SmsDraftComposer
import com.localmed.integration.whatsapp.WhatsAppHandoff
import com.localmed.knowledge.api.KnowledgeRecord
import com.localmed.knowledge.api.KnowledgeRepository
import com.localmed.knowledge.api.ReviewStatus
import com.localmed.knowledge.medical.KnowledgeJsonlImporter
import com.localmed.storage.database.RoomTrainingDatasetRepository
import com.localmed.storage.files.ImportedModelBundle
import com.localmed.storage.files.ModelArtifactResolver
import com.localmed.storage.files.ModelBundleImporter
import com.localmed.storage.files.TrainingDatasetImporter
import com.localmed.storage.files.TrustedPublisherKeyImporter
import com.localmed.tools.api.ToolExecutor
import com.localmed.tools.api.ToolRequest
import com.localmed.tools.registry.BuiltInToolRegistry.Companion.PUBMED_SEARCH_ID
import com.localmed.ui.LocalMedUiState
import com.localmed.ui.ModelUi
import com.localmed.ui.ResearchArticleUi
import com.localmed.ui.TrainingDatasetUi
import com.localmed.ui.TrustedPublisherUi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@HiltViewModel
class LocalMedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationGateway: ConversationGateway,
    private val knowledgeRepository: KnowledgeRepository,
    private val knowledgeImporter: KnowledgeJsonlImporter,
    private val modelBundleImporter: ModelBundleImporter,
    private val modelArtifactResolver: ModelArtifactResolver,
    private val modelRegistry: ModelRegistry,
    private val trustedPublisherRepository: TrustedPublisherRepository,
    private val keyImporter: TrustedPublisherKeyImporter,
    private val encryptedArtifactStore: EncryptedArtifactStore,
    private val inferenceEngine: InferenceEngine,
    private val tokenizerFactory: RustTokenizerFactory,
    private val trainingDatasetImporter: TrainingDatasetImporter,
    private val trainingDatasetRepository: TrainingDatasetRepository,
    private val toolExecutor: ToolExecutor,
    private val preferences: AppPreferencesStore,
    private val logger: StructuredLogger,
    private val smsDraftComposer: SmsDraftComposer,
    private val whatsAppHandoff: WhatsAppHandoff
) : ViewModel() {
    private val _state = MutableStateFlow(
        LocalMedUiState(
            webSearchBuildAvailable = BuildConfig.WEB_SEARCH_BUILD,
            deviceStatus = deviceStatus()
        )
    )
    val state = _state.asStateFlow()
    private val sessionId = UUID.randomUUID().toString()
    private var preferenceJob: Job? = null
    private var refreshJob: Job? = null

    init {
        encryptedArtifactStore.purgePlaintextCache()
        preferenceJob = viewModelScope.launch {
            preferences.data.collectLatest { prefs ->
                _state.update { it.copy(webSearchEnabled = BuildConfig.WEB_SEARCH_BUILD && prefs.webSearchEnabled) }
            }
        }
        viewModelScope.launch {
            refreshData()
            restoreActiveModel()
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { refreshData() }
    }

    fun submitQuestion(text: String) {
        val safeText = text.trim().take(MAX_QUESTION_CHARS)
        if (safeText.isBlank()) {
            showNotice("Enter an educational or research question.")
            return
        }
        viewModelScope.launch {
            setBusy(true)
            _state.update { it.copy(response = null, notice = null) }
            try {
                val response = withContext(Dispatchers.Default) {
                    conversationGateway.process(NormalizedMessage(sessionId = sessionId, text = safeText))
                }
                if (response == null) {
                    _state.update { it.copy(response = unavailableResponse()) }
                } else {
                    _state.update { it.copy(response = response) }
                    logger.log(
                        StructuredLogEvent(
                            module = "conversation.runtime",
                            severity = LogSeverity.INFO,
                            event = "turn_completed",
                            modelVersion = response.modelVersion,
                            fields = mapOf(
                                "response_state" to response.responseState.name,
                                "generated_by_model" to response.generatedByModel.toString(),
                                "citation_count" to response.citations.size.toString()
                            )
                        )
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        response = OutgoingMessage(
                            responseState = ResponseState.UNCERTAIN,
                            content = "Local processing failed. No generated answer was returned; retry or consult a qualified clinician.",
                            generatedByModel = false
                        )
                    )
                }
                logger.log(StructuredLogEvent("", Instant.now(), "conversation.runtime", LogSeverity.ERROR, "local_processing_failed"))
            } finally {
                setBusy(false)
            }
        }
    }

    fun importKnowledge(uri: Uri) {
        viewModelScope.launch {
            setBusy(true)
            try {
                val result = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Selected JSONL file could not be opened.")
                    knowledgeImporter.importJsonl(input)
                }
                val inserted = if (result.records.isEmpty()) 0 else withContext(Dispatchers.IO) {
                    knowledgeRepository.upsert(result.records)
                }
                val issueSummary = result.issues.firstOrNull()?.let { " First issue at line ${it.line}: ${it.message}" }.orEmpty()
                showNotice("Imported $inserted record(s) as UNREVIEWED. ${result.issues.size} row(s) were not imported.$issueSummary")
                refreshData()
            } catch (exception: Exception) {
                showNotice("Knowledge import failed: ${exception.message ?: "invalid file"}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun importTrustedPublisher(uri: Uri, keyId: String, displayName: String, fingerprint: String) {
        viewModelScope.launch {
            setBusy(true)
            try {
                val publisher = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Selected public key file could not be opened.")
                    keyImporter.importRsaPublicKey(input, keyId, displayName, fingerprint)
                }
                val saved = withContext(Dispatchers.IO) { trustedPublisherRepository.addTrustedPublisher(publisher) }
                if (saved) enforceActivePublisherTrust(publisher.keyId)
                showNotice(if (saved) "Publisher key trusted after fingerprint confirmation: ${publisher.keyId}." else "Publisher key could not be stored.")
                refreshData()
            } catch (exception: Exception) {
                showNotice("Publisher key was not trusted: ${exception.message ?: "invalid key"}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun importModel(uri: Uri, publisherKeyId: String) {
        viewModelScope.launch {
            setBusy(true)
            try {
                val bundleAndSaved = withContext(Dispatchers.IO) {
                    val publisher = trustedPublisherRepository.getTrustedPublisher(publisherKeyId)
                        ?: throw IllegalStateException("The selected publisher is no longer trusted.")
                    val bundle = modelBundleImporter.importBundle(uri, publisher)
                    val existingEntry = modelRegistry.get(bundle.manifest.modelId, bundle.manifest.modelVersion)
                    val saved = modelRegistry.register(
                        ModelRegistryEntry(
                            modelId = bundle.manifest.modelId,
                            modelVersion = bundle.manifest.modelVersion,
                            manifestJson = bundle.manifestJson,
                            status = ModelValidationState.IMPORTED,
                            importedAt = Instant.now(),
                            modelSha256 = bundle.manifest.modelSha256,
                            tokenizerSha256 = bundle.manifest.tokenizerSha256,
                            isActive = false
                        )
                    )
                    val entryReferencesArtifacts = existingEntry?.let { previous ->
                        runCatching { ModelManifest.parse(previous.manifestJson).storageId() == bundle.artifactStorageId }.getOrDefault(false)
                    } == true
                    if (!saved && !bundle.reusedExistingEncryptedArtifacts && !entryReferencesArtifacts) {
                        encryptedArtifactStore.deleteArtifact(bundle.artifactStorageId)
                    }
                    bundle to saved
                }
                val (bundle, saved) = bundleAndSaved
                showNotice(if (saved) {
                    "Signed model ${bundle.manifest.modelId} ${bundle.manifest.modelVersion} imported and encrypted. It is not active until compatibility checks pass and you activate it."
                } else {
                    "This model ID and version is already registered. Existing encrypted artifacts were preserved; no model was activated."
                })
                refreshData()
            } catch (exception: Exception) {
                showNotice("Model import failed: ${exception.message ?: "invalid bundle"}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun validateModel(modelId: String, version: String) {
        viewModelScope.launch {
            setBusy(true)
            var runtimeRejected = false
            val previous = withContext(Dispatchers.IO) { modelRegistry.list().firstOrNull { it.isActive } }
            try {
                val entry = withContext(Dispatchers.IO) { modelRegistry.get(modelId, version) }
                    ?: throw IllegalStateException("Model registry entry was not found.")
                val descriptor = withContext(Dispatchers.IO) { createVerifiedDescriptor(entry) }
                when (val loaded = inferenceEngine.load(descriptor)) {
                    is LoadResult.Rejected -> {
                        runtimeRejected = true
                        throw IllegalStateException(loaded.reason)
                    }
                    is LoadResult.Loaded -> Unit
                }
                // One real token is generated only to test tokenizer/ONNX compatibility; it is never shown as medical output.
                inferenceEngine.generate(GenerationRequest("Local runtime compatibility check.", maxNewTokens = 1))
                inferenceEngine.unload()
                withContext(Dispatchers.IO) {
                    check(modelRegistry.markSmokeTested(modelId, version)) { "Smoke-test result could not be saved." }
                }
                showNotice("Runtime smoke test passed. This confirms only technical compatibility, not medical safety or clinical validity. Activate this model explicitly to use it.")
            } catch (exception: Exception) {
                runCatching { inferenceEngine.unload() }
                if (runtimeRejected) withContext(Dispatchers.IO) {
                    modelRegistry.markRejected(modelId, version, exception.message ?: "Runtime compatibility check failed.")
                }
                showNotice("Model was not smoke-tested: ${exception.message ?: "compatibility check failed"}")
            } finally {
                if (previous != null && (previous.modelId != modelId || previous.modelVersion != version)) {
                    runCatching { restoreModel(previous) }.onFailure {
                        showNotice("Candidate model check finished, but the previously active model could not be restored. ${it.message ?: "Check the model status."}")
                    }
                }
                refreshData()
                setBusy(false)
            }
        }
    }

    fun activateModel(modelId: String, version: String) {
        viewModelScope.launch {
            setBusy(true)
            var activationSucceeded = false
            val previous = withContext(Dispatchers.IO) { modelRegistry.list().firstOrNull { it.isActive } }
            try {
                val entry = withContext(Dispatchers.IO) { modelRegistry.get(modelId, version) }
                    ?: throw IllegalStateException("Model registry entry was not found.")
                require(entry.status == ModelValidationState.SMOKE_TESTED || entry.isActive) {
                    "Only a smoke-tested model can be activated."
                }
                val descriptor = withContext(Dispatchers.IO) { createVerifiedDescriptor(entry) }
                when (val loaded = inferenceEngine.load(descriptor)) {
                    is LoadResult.Rejected -> throw IllegalStateException(loaded.reason)
                    is LoadResult.Loaded -> Unit
                }
                inferenceEngine.generate(GenerationRequest("Local activation compatibility check.", maxNewTokens = 1))
                val activated = withContext(Dispatchers.IO) { modelRegistry.activate(modelId, version) }
                check(activated) { "Atomic model activation failed; the previous registry selection was retained." }
                activationSucceeded = true
                runCatching { preferences.setActiveModelId(modelId) }
                showNotice("${entry.modelId} ${entry.modelVersion} is active for local inference. It is not clinically validated.")
            } catch (exception: Exception) {
                runCatching { inferenceEngine.unload() }
                showNotice("Model was not activated: ${exception.message ?: "compatibility check failed"}")
            } finally {
                if (!activationSucceeded && previous != null) {
                    runCatching { restoreModel(previous) }.onFailure {
                        showNotice("The previous active model could not be restored: ${it.message ?: "runtime unavailable"}")
                    }
                }
                refreshData()
                setBusy(false)
            }
        }
    }

    fun removeTrustedPublisher(keyId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { trustedPublisherRepository.removeTrustedPublisher(keyId) }
            enforceActivePublisherTrust(keyId)
            showNotice("Publisher trust removed. Any active model signed by this key was unloaded; encrypted artifacts remain on-device.")
            refreshData()
        }
    }

    private suspend fun enforceActivePublisherTrust(keyId: String) {
        val active = withContext(Dispatchers.IO) { modelRegistry.list().firstOrNull { it.isActive } } ?: return
        val manifest = runCatching { ModelManifest.parse(active.manifestJson) }.getOrNull()
        if (manifest != null && manifest.publisherKeyId != keyId) return
        val publisher = if (manifest == null) null else withContext(Dispatchers.IO) {
            trustedPublisherRepository.getTrustedPublisher(keyId)
        }
        if (manifest != null && publisher != null && ModelSignatureVerifier.verify(manifest, publisher)) return
        runCatching { inferenceEngine.unload() }
        withContext(Dispatchers.IO) { modelRegistry.deactivateAll() }
        runCatching { preferences.setActiveModelId("") }
        _state.update { it.copy(loadedModelLabel = null) }
    }

    fun reviewKnowledge(id: String, status: ReviewStatus, notes: String) {
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) { knowledgeRepository.setReviewStatus(id, status, notes) }
            showNotice(if (changed) "Record ${status.name.lowercase()} locally. This is not an independent clinical review." else "Record was not found.")
            refreshData()
        }
    }

    fun deleteKnowledge(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { knowledgeRepository.delete(id) }
            showNotice("Knowledge record deleted from this device.")
            refreshData()
        }
    }

    fun importTrainingDataset(uri: Uri, displayName: String, source: String, license: String) {
        viewModelScope.launch {
            setBusy(true)
            try {
                val record = withContext(Dispatchers.IO) {
                    trainingDatasetImporter.importDataset(uri, displayName, source, license)
                }
                showNotice("Validated ${record.exampleCount} examples and stored the dataset encrypted. Training is unavailable in this build; nothing was trained.")
                refreshData()
            } catch (exception: Exception) {
                showNotice("Training dataset import failed; no dataset was activated: ${exception.message ?: "invalid dataset"}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun deleteTrainingDataset(id: String) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { trainingDatasetImporter.delete(id) }
            showNotice(if (deleted) "Encrypted dataset and metadata deleted." else "Dataset metadata was not found.")
            refreshData()
        }
    }

    fun searchPubMed(query: String) {
        if (!BuildConfig.WEB_SEARCH_BUILD || !_state.value.webSearchEnabled) {
            showNotice("PubMed search is unavailable or disabled in this build.")
            return
        }
        val safeQuery = query.trim().take(500)
        if (safeQuery.length !in 3..500) {
            showNotice("Enter at least three PubMed search characters.")
            return
        }
        viewModelScope.launch {
            setBusy(true)
            _state.update { it.copy(researchResults = emptyList(), researchQueryHash = null, notice = null) }
            try {
                val input = buildJsonObject {
                    put("query", safeQuery)
                    put("maxResults", 3)
                }.toString()
                val result = toolExecutor.execute(
                    ToolRequest(
                        toolId = PUBMED_SEARCH_ID,
                        inputJson = input,
                        userConfirmed = true
                    )
                )
                if (!result.success) {
                    val message = runCatching {
                        Json.parseToJsonElement(result.payloadJson).jsonObject["error"]?.jsonPrimitive?.content
                    }.getOrNull() ?: result.errorCode ?: "Search authorization was denied."
                    showNotice("PubMed search was not run: $message")
                } else {
                    val payload = Json.parseToJsonElement(result.payloadJson).jsonObject
                    val articles = payload["results"]?.jsonArray.orEmpty().mapNotNull { raw ->
                        runCatching {
                            val item = raw.jsonObject
                            ResearchArticleUi(
                                pmid = item["pmid"]!!.jsonPrimitive.content,
                                title = item["title"]!!.jsonPrimitive.content,
                                journal = item["journal"]?.jsonPrimitive?.content.orEmpty(),
                                publicationDate = item["publicationDate"]?.jsonPrimitive?.content.orEmpty(),
                                abstractText = item["abstract"]?.jsonPrimitive?.content.orEmpty(),
                                url = item["url"]!!.jsonPrimitive.content,
                                sha256 = item["sha256"]!!.jsonPrimitive.content
                            )
                        }.getOrNull()
                    }
                    _state.update { it.copy(researchResults = articles, researchQueryHash = payload["queryHash"]?.jsonPrimitive?.content) }
                    showNotice("Received ${articles.size} untrusted PubMed record(s). Results were not added to local evidence.")
                }
            } catch (exception: Exception) {
                showNotice("PubMed search failed: ${exception.message ?: "network or parsing error"}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val allowed = BuildConfig.WEB_SEARCH_BUILD && enabled
            preferences.setWebSearchEnabled(allowed)
            showNotice(if (allowed) "Optional PubMed tool enabled. Each search still requires confirmation." else "PubMed network access disabled.")
        }
    }

    fun openSmsDraft(phone: String, body: String) {
        runCatching { smsDraftComposer.openDraft(phone, body) }
            .onFailure { showNotice("SMS draft could not be opened: ${it.message ?: "no compatible SMS app"}") }
    }

    fun handoffWhatsApp(phone: String, body: String) {
        runCatching { whatsAppHandoff.openChat(phone, body) }
            .onFailure { showNotice("Official WhatsApp handoff could not be opened: ${it.message ?: "no compatible handler"}") }
    }

    fun dismissNotice() { _state.update { it.copy(notice = null) } }

    private suspend fun refreshData() {
        try {
            val records = withContext(Dispatchers.IO) { knowledgeRepository.listAll(300) }
            val models = withContext(Dispatchers.IO) { modelRegistry.list() }.mapNotNull { entry ->
                val manifest = runCatching { ModelManifest.parse(entry.manifestJson) }.getOrNull() ?: return@mapNotNull null
                ModelUi(
                    id = entry.modelId,
                    version = entry.modelVersion,
                    status = entry.status,
                    isActive = entry.isActive,
                    sha256 = entry.modelSha256,
                    tokenizerSha256 = entry.tokenizerSha256,
                    source = manifest.source,
                    license = manifest.license,
                    architecture = manifest.architecture.kind,
                    importedAt = entry.importedAt
                )
            }
            val publishers = withContext(Dispatchers.IO) { trustedPublisherRepository.listTrustedPublishers() }.map {
                TrustedPublisherUi(it.keyId, it.displayName, it.fingerprintSha256)
            }
            val datasets = withContext(Dispatchers.IO) { trainingDatasetRepository.list() }.map {
                TrainingDatasetUi(it.id, it.displayName, it.sha256, it.exampleCount, it.source, it.license, it.importedAt)
            }
            val loadedEntry = withContext(Dispatchers.IO) { modelRegistry.list().firstOrNull { it.isActive } }
            _state.update {
                it.copy(
                    isInitialized = true,
                    models = models,
                    knowledgeRecords = records,
                    trustedPublishers = publishers,
                    trainingDatasets = datasets,
                    loadedModelLabel = if (inferenceEngine.isLoaded() && loadedEntry != null) "${loadedEntry.modelId} ${loadedEntry.modelVersion}" else null,
                    deviceStatus = deviceStatus()
                )
            }
        } catch (exception: Exception) {
            showNotice("Local database could not be read: ${exception.message ?: "storage unavailable"}")
            _state.update { it.copy(isInitialized = true) }
        }
    }

    private suspend fun restoreActiveModel() {
        val active = withContext(Dispatchers.IO) { modelRegistry.list().firstOrNull { it.isActive } } ?: return
        if (!tokenizerFactory.isNativeAvailable()) {
            showNotice("A model is registered as active, but the Rust tokenizer library is unavailable in this APK. Rebuild with native Android libraries before using inference.")
            return
        }
        try {
            val descriptor = withContext(Dispatchers.IO) { createVerifiedDescriptor(active) }
            when (val result = inferenceEngine.load(descriptor)) {
                is LoadResult.Rejected -> {
                    withContext(Dispatchers.IO) { modelRegistry.markRejected(active.modelId, active.modelVersion, result.reason) }
                    throw IllegalStateException(result.reason)
                }
                is LoadResult.Loaded -> Unit
            }
            inferenceEngine.generate(GenerationRequest("Local model startup compatibility check.", maxNewTokens = 1))
            _state.update { it.copy(loadedModelLabel = "${active.modelId} ${active.modelVersion}") }
        } catch (exception: Exception) {
            runCatching { inferenceEngine.unload() }
            withContext(Dispatchers.IO) {
                modelRegistry.markRejected(active.modelId, active.modelVersion, exception.message ?: "Startup check failed.")
                modelRegistry.deactivateAll()
            }
            showNotice("Previously active model failed its startup verification and was deactivated: ${exception.message ?: "runtime check failed"}")
        }
    }

    private suspend fun restoreModel(entry: ModelRegistryEntry) {
        val descriptor = withContext(Dispatchers.IO) { createVerifiedDescriptor(entry) }
        when (val result = inferenceEngine.load(descriptor)) {
            is LoadResult.Rejected -> throw IllegalStateException(result.reason)
            is LoadResult.Loaded -> Unit
        }
        inferenceEngine.generate(GenerationRequest("Local model restoration compatibility check.", maxNewTokens = 1))
        _state.update { it.copy(loadedModelLabel = "${entry.modelId} ${entry.modelVersion}") }
    }

    private suspend fun createVerifiedDescriptor(entry: ModelRegistryEntry): com.localmed.ai.api.ModelDescriptor {
        val manifest = ModelManifest.parse(entry.manifestJson)
        val validation = ModelManifestValidator.validate(manifest)
        require(validation.valid) { validation.errors.joinToString(" ") }
        require(manifest.modelId == entry.modelId && manifest.modelVersion == entry.modelVersion) { "Registry identity does not match the signed manifest." }
        require(manifest.modelSha256.equals(entry.modelSha256, ignoreCase = true)) { "Registry model hash does not match its manifest." }
        require(manifest.tokenizerSha256.equals(entry.tokenizerSha256, ignoreCase = true)) { "Registry tokenizer hash does not match its manifest." }
        val trustedPublisher = trustedPublisherRepository.getTrustedPublisher(manifest.publisherKeyId)
            ?: throw IllegalStateException("The model publisher key is not currently trusted.")
        require(ModelSignatureVerifier.verify(manifest, trustedPublisher)) { "Publisher signature or fingerprint verification failed." }
        val id = manifest.storageId()
        val modelFile = encryptedArtifactStore.encryptedFile(id, ModelManifest.MODEL_FILE)
        val tokenizerFile = encryptedArtifactStore.encryptedFile(id, ModelManifest.TOKENIZER_FILE)
        require(modelFile.isFile && tokenizerFile.isFile) { "Encrypted model files are missing." }
        val bundle = ImportedModelBundle(manifest, entry.manifestJson, id, modelFile, tokenizerFile)
        return modelArtifactResolver.materialize(bundle)
    }


    private suspend fun setBusy(busy: Boolean) {
        _state.update { it.copy(isBusy = busy) }
    }

    private fun showNotice(message: String) {
        _state.update { it.copy(notice = message.take(1_000)) }
    }

    private fun deviceStatus(): String {
        val nativeStatus = if (tokenizerFactory.isNativeAvailable()) "Rust tokenizer ready" else "Rust tokenizer unavailable"
        val api = "Android API ${Build.VERSION.SDK_INT}"
        return "$api · ONNX Runtime CPU path included · $nativeStatus"
    }

    private fun unavailableResponse() = OutgoingMessage(
        responseState = ResponseState.UNCERTAIN,
        content = "No local response was produced. The assistant did not generate an answer.",
        generatedByModel = false
    )

    override fun onCleared() {
        super.onCleared()
        preferenceJob?.cancel()
        viewModelScope.launch(NonCancellable) { runCatching { inferenceEngine.unload() } }
    }

    companion object { private const val MAX_QUESTION_CHARS = 4_000 }
}
