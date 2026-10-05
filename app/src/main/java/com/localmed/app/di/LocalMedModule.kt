package com.localmed.app.di

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.localmed.ai.api.InferenceEngine
import com.localmed.ai.api.RuntimeProfiler
import com.localmed.ai.api.TokenizerFactory
import com.localmed.ai.model.ModelRegistry
import com.localmed.ai.model.TrustedPublisherRepository
import com.localmed.ai.retrieval.LocalEvidenceRetriever
import com.localmed.ai.safety.MedicalOutputValidator
import com.localmed.ai.safety.MedicalSafetyPolicy
import com.localmed.ai.tokenizer.RustTokenizerFactory
import com.localmed.ai.training.TrainingDatasetRepository
import com.localmed.ai.training.TrainingDatasetValidator
import com.localmed.app.BuildConfig
import com.localmed.app.data.AppPreferencesStore
import com.localmed.conversation.api.ConversationGateway
import com.localmed.conversation.runtime.LocalConversationGateway
import com.localmed.core.logging.RotatingJsonlLogger
import com.localmed.core.logging.StructuredLogEvent
import com.localmed.core.logging.StructuredLogger
import com.localmed.core.security.EncryptedArtifactStore
import com.localmed.integration.sms.SmsDraftComposer
import com.localmed.integration.whatsapp.WhatsAppHandoff
import com.localmed.knowledge.api.KnowledgeRepository
import com.localmed.knowledge.medical.KnowledgeJsonlImporter
import com.localmed.storage.database.LocalMedDatabase
import com.localmed.storage.database.RoomKnowledgeRepository
import com.localmed.storage.database.RoomModelRegistry
import com.localmed.storage.database.RoomTrainingDatasetRepository
import com.localmed.storage.database.RoomTrustedPublisherRepository
import com.localmed.storage.files.ModelArtifactResolver
import com.localmed.storage.files.ModelBundleImporter
import com.localmed.storage.files.TrainingDatasetImporter
import com.localmed.storage.files.TrustedPublisherKeyImporter
import com.localmed.tools.api.PermissionController
import com.localmed.tools.api.ToolExecutor
import com.localmed.tools.api.ToolRequest
import com.localmed.tools.api.ToolResult
import com.localmed.tools.executor.PolicyToolExecutor
import com.localmed.tools.executor.ToolImplementation
import com.localmed.tools.permissions.ToolPolicy
import com.localmed.tools.registry.BuiltInToolRegistry
import com.localmed.tools.registry.ToolRegistry
import com.localmed.tools.registry.BuiltInToolRegistry.Companion.PUBMED_SEARCH_ID
import com.localmed.tools.websearch.PubMedEutilsSearchProvider
import com.localmed.tools.websearch.SearchProvider
import com.localmed.ai.inference.OrtInferenceEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Module
@InstallIn(SingletonComponent::class)
object LocalMedModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LocalMedDatabase = LocalMedDatabase.create(context)

    @Provides
    @Singleton
    fun provideEncryptedArtifactStore(@ApplicationContext context: Context) = EncryptedArtifactStore(context)

    @Provides
    @Singleton
    fun provideKnowledgeRepository(database: LocalMedDatabase): KnowledgeRepository = RoomKnowledgeRepository(database.knowledgeDao())

    @Provides
    @Singleton
    fun provideModelRegistry(database: LocalMedDatabase): ModelRegistry = RoomModelRegistry(database)

    @Provides
    @Singleton
    fun provideTrustedPublisherRepository(database: LocalMedDatabase): TrustedPublisherRepository =
        RoomTrustedPublisherRepository(database.trustedPublisherDao())

    @Provides
    @Singleton
    fun provideTrainingDatasetRepository(database: LocalMedDatabase): TrainingDatasetRepository =
        RoomTrainingDatasetRepository(database.trainingDatasetDao())

    @Provides
    @Singleton
    fun provideKnowledgeJsonlImporter() = KnowledgeJsonlImporter()

    @Provides
    @Singleton
    fun provideTrainingDatasetValidator() = TrainingDatasetValidator()

    @Provides
    @Singleton
    fun provideModelBundleImporter(
        @ApplicationContext context: Context,
        encryptedStore: EncryptedArtifactStore
    ) = ModelBundleImporter(context, encryptedStore)

    @Provides
    @Singleton
    fun provideModelArtifactResolver(
        @ApplicationContext context: Context,
        encryptedStore: EncryptedArtifactStore
    ) = ModelArtifactResolver(encryptedStore, context.cacheDir.resolve("verified-artifacts"))

    @Provides
    @Singleton
    fun provideTrainingDatasetImporter(
        @ApplicationContext context: Context,
        validator: TrainingDatasetValidator,
        repository: TrainingDatasetRepository,
        encryptedStore: EncryptedArtifactStore
    ) = TrainingDatasetImporter(context, validator, repository, encryptedStore)

    @Provides
    @Singleton
    fun provideTrustedPublisherKeyImporter() = TrustedPublisherKeyImporter()

    @Provides
    @Singleton
    fun provideRustTokenizerFactory() = RustTokenizerFactory()

    @Provides
    @Singleton
    fun provideTokenizerFactory(factory: RustTokenizerFactory): TokenizerFactory = factory

    @Provides
    @Singleton
    fun provideOrtInferenceEngine(
        tokenizerFactory: TokenizerFactory,
        @ApplicationContext context: Context
    ) = OrtInferenceEngine(tokenizerFactory, context.cacheDir.resolve("verified-artifacts"))

    @Provides
    @Singleton
    fun provideInferenceEngine(engine: OrtInferenceEngine): InferenceEngine = engine

    @Provides
    @Singleton
    fun provideRuntimeProfiler(engine: OrtInferenceEngine): RuntimeProfiler = engine

    @Provides
    @Singleton
    fun provideConversationGateway(engine: InferenceEngine, repository: KnowledgeRepository): ConversationGateway =
        LocalConversationGateway(
            inferenceEngine = engine,
            retriever = LocalEvidenceRetriever(repository),
            safetyPolicy = MedicalSafetyPolicy(),
            outputValidator = MedicalOutputValidator()
        )

    @Provides
    @Singleton
    fun provideToolRegistry(): ToolRegistry = BuiltInToolRegistry()

    @Provides
    @Singleton
    fun provideSearchProvider(): SearchProvider = PubMedEutilsSearchProvider()

    @Provides
    @Singleton
    fun providePermissionController(
        @ApplicationContext context: Context,
        preferences: AppPreferencesStore
    ): PermissionController = AppToolPermissionController(context, preferences, BuildConfig.WEB_SEARCH_BUILD)

    @Provides
    @Singleton
    fun provideToolExecutor(
        registry: ToolRegistry,
        permissionController: PermissionController,
        searchProvider: SearchProvider,
        logger: StructuredLogger
    ): ToolExecutor {
        val pubmedImplementation = ToolImplementation { input ->
            val query = requireNotNull(input["query"]?.jsonPrimitive?.content) { "Query is required." }
            val maxResults = input["maxResults"]?.jsonPrimitive?.intOrNull ?: 3
            val search = withContext(Dispatchers.IO) { searchProvider.search(query, maxResults) }
            val payload = buildJsonObject {
                put("results", buildJsonArray {
                    search.articles.forEach { article ->
                        add(buildJsonObject {
                            put("pmid", article.pmid)
                            put("title", article.title)
                            put("journal", article.journal)
                            put("publicationDate", article.publicationDate)
                            put("abstract", article.abstractText)
                            put("url", article.url)
                            put("sha256", article.contentSha256)
                        })
                    }
                })
                put("queryHash", search.queryHash)
                put("retrievedAt", search.retrievedAt.toString())
            }.toString()
            logger.log(
                StructuredLogEvent(
                    module = "tool.pubmed",
                    severity = com.localmed.core.logging.LogSeverity.INFO,
                    event = "search_completed",
                    fields = mapOf("query_hash" to search.queryHash, "result_count" to search.articles.size.toString())
                )
            )
            ToolResult(success = true, payloadJson = payload, sources = search.sources, untrusted = true)
        }
        return PolicyToolExecutor(
            registry = registry,
            policy = ToolPolicy(permissionController),
            implementations = mapOf(PUBMED_SEARCH_ID to pubmedImplementation)
        )
    }

    @Provides
    @Singleton
    fun provideStructuredLogger(@ApplicationContext context: Context): StructuredLogger =
        RotatingJsonlLogger(context.filesDir.resolve("redacted-logs"))

    @Provides
    @Singleton
    fun provideSmsDraftComposer(@ApplicationContext context: Context) = SmsDraftComposer(context)

    @Provides
    @Singleton
    fun provideWhatsAppHandoff(@ApplicationContext context: Context) = WhatsAppHandoff(context)
}

private class AppToolPermissionController(
    context: Context,
    private val preferences: AppPreferencesStore,
    private val webSearchBuildAvailable: Boolean
) : PermissionController {
    private val appContext = context.applicationContext

    override suspend fun isPermissionGranted(permission: String): Boolean {
        if (!webSearchBuildAvailable || permission != android.Manifest.permission.INTERNET) return false
        return appContext.packageManager.checkPermission(permission, appContext.packageName) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun isToolAuthorized(toolId: String): Boolean {
        if (toolId != PUBMED_SEARCH_ID || !webSearchBuildAvailable) return false
        if (!hasValidatedNetwork()) return false
        return preferences.data.first().webSearchEnabled
    }

    private fun hasValidatedNetwork(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
