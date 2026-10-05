package com.localmed.storage.database

import android.content.Context
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.localmed.ai.model.ModelRegistry
import com.localmed.ai.model.ModelRegistryEntry
import com.localmed.ai.model.ModelValidationState
import com.localmed.ai.training.TrainingDatasetRecord
import com.localmed.ai.training.TrainingDatasetRepository
import com.localmed.ai.model.TrustedPublisher
import com.localmed.ai.model.TrustedPublisherRepository
import com.localmed.conversation.api.ResponseCitation
import com.localmed.core.protocol.KnowledgeRecordProto
import com.localmed.knowledge.api.EvidenceLevel
import com.localmed.knowledge.api.KnowledgeHit
import com.localmed.knowledge.api.KnowledgeRecord
import com.localmed.knowledge.api.KnowledgeRecordType
import com.localmed.knowledge.api.KnowledgeQuery
import com.localmed.knowledge.api.KnowledgeRepository
import com.localmed.knowledge.api.ReviewStatus
import kotlinx.coroutines.Dispatchers
import java.time.Instant

@Entity(
    tableName = "knowledge_records",
    indices = [
        Index(value = ["stable_id"], unique = true),
        Index(value = ["review_status"]),
        Index(value = ["specialty"]),
        Index(value = ["record_type"])
    ]
)
data class KnowledgeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "local_id") val localId: Long = 0,
    @ColumnInfo(name = "stable_id") val stableId: String,
    @ColumnInfo(name = "record_type") val recordType: String,
    val specialty: String,
    val title: String,
    val content: String,
    val source: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "author_or_organization") val authorOrOrganization: String,
    @ColumnInfo(name = "publication_year") val publicationYear: Int?,
    @ColumnInfo(name = "evidence_level") val evidenceLevel: String,
    @ColumnInfo(name = "review_status") val reviewStatus: String,
    val revision: Int,
    @ColumnInfo(name = "effective_date") val effectiveDate: String?,
    @ColumnInfo(name = "review_or_expiration_date") val reviewOrExpirationDate: String?,
    val license: String,
    @ColumnInfo(name = "reviewer_notes") val reviewerNotes: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "canonical_proto") val canonicalProto: ByteArray
)

@Fts5(contentEntity = KnowledgeEntity::class, contentRowId = "local_id")
@Entity(tableName = "knowledge_fts")
data class KnowledgeFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val content: String,
    val specialty: String
)

data class KnowledgeSearchRow(
    @Embedded val record: KnowledgeEntity,
    @ColumnInfo(name = "fts_rank") val ftsRank: Double
)

@Entity(tableName = "model_registry", indices = [Index(value = ["model_id", "model_version"], unique = true)])
data class ModelRegistryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    @ColumnInfo(name = "manifest_json") val manifestJson: String,
    val status: String,
    @ColumnInfo(name = "imported_at_epoch_ms") val importedAtEpochMs: Long,
    @ColumnInfo(name = "model_sha256") val modelSha256: String,
    @ColumnInfo(name = "tokenizer_sha256") val tokenizerSha256: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String? = null
)

@Entity(tableName = "trusted_publishers")
data class TrustedPublisherEntity(
    @PrimaryKey @ColumnInfo(name = "key_id") val keyId: String,
    @ColumnInfo(name = "public_key_x509_base64") val publicKeyX509Base64: String,
    @ColumnInfo(name = "fingerprint_sha256") val fingerprintSha256: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "trust_granted_at_epoch_ms") val trustGrantedAtEpochMs: Long
)

@Entity(tableName = "memory_records", indices = [Index(value = ["scope"]), Index(value = ["consent_state"])])
data class EncryptedMemoryEntity(
    @PrimaryKey @ColumnInfo(name = "memory_id") val memoryId: String,
    val scope: String,
    @ColumnInfo(name = "ciphertext_envelope") val ciphertextEnvelope: ByteArray,
    @ColumnInfo(name = "source_label") val sourceLabel: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "last_used_at_epoch_ms") val lastUsedAtEpochMs: Long?,
    val confidence: Double,
    @ColumnInfo(name = "consent_state") val consentState: String,
    @ColumnInfo(name = "retention_policy") val retentionPolicy: String,
    @ColumnInfo(name = "deleted_at_epoch_ms") val deletedAtEpochMs: Long?
)

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "locale_tag") val localeTag: String,
    @ColumnInfo(name = "consent_to_persist") val consentToPersist: Boolean
)

@Entity(tableName = "conversation_messages", indices = [Index(value = ["session_id", "created_at_epoch_ms"])])
data class EncryptedConversationMessageEntity(
    @PrimaryKey @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "ciphertext_envelope") val ciphertextEnvelope: ByteArray,
    @ColumnInfo(name = "response_state") val responseState: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "model_version") val modelVersion: String?,
    @ColumnInfo(name = "citation_ids_proto") val citationIdsProto: ByteArray
)

@Entity(tableName = "training_datasets", indices = [Index(value = ["sha256"], unique = true)])
data class TrainingDatasetEntity(
    @PrimaryKey @ColumnInfo(name = "dataset_id") val datasetId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "sha256") val sha256: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "example_count") val exampleCount: Int,
    @ColumnInfo(name = "imported_at_epoch_ms") val importedAtEpochMs: Long,
    val source: String,
    val license: String
)

@Entity(tableName = "training_jobs", indices = [Index(value = ["status", "updated_at_epoch_ms"])])
data class TrainingJobEntity(
    @PrimaryKey @ColumnInfo(name = "job_id") val jobId: String,
    val status: String,
    @ColumnInfo(name = "dataset_sha256") val datasetSha256: String,
    @ColumnInfo(name = "base_model_sha256") val baseModelSha256: String,
    @ColumnInfo(name = "configuration_proto") val configurationProto: ByteArray,
    @ColumnInfo(name = "checkpoint_relative_path") val checkpointRelativePath: String?,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)

@Entity(tableName = "tool_permission_decisions", indices = [Index(value = ["tool_id", "updated_at_epoch_ms"])])
data class ToolPermissionDecisionEntity(
    @PrimaryKey @ColumnInfo(name = "tool_id") val toolId: String,
    val decision: String,
    @ColumnInfo(name = "scope_label") val scopeLabel: String,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)

@Entity(tableName = "audit_events", indices = [Index(value = ["timestamp_epoch_ms"]), Index(value = ["correlation_id"])])
data class AuditEventEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "correlation_id") val correlationId: String,
    @ColumnInfo(name = "timestamp_epoch_ms") val timestampEpochMs: Long,
    val module: String,
    val severity: String,
    @ColumnInfo(name = "event_name") val eventName: String,
    @ColumnInfo(name = "model_version") val modelVersion: String?,
    @ColumnInfo(name = "redacted_fields_proto") val redactedFieldsProto: ByteArray
)

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: KnowledgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<KnowledgeEntity>): List<Long>

    @Query("""
        SELECT r.*, bm25(knowledge_fts) AS fts_rank
        FROM knowledge_records AS r
        JOIN knowledge_fts ON knowledge_fts.rowid = r.local_id
        WHERE knowledge_fts MATCH :ftsQuery
          AND (:specialty IS NULL OR r.specialty = :specialty)
          AND (
              :verifiedOnly = 0 OR (
                  r.review_status = 'VERIFIED'
                  AND (r.effective_date IS NULL OR r.effective_date <= :asOfDate)
                  AND (r.review_or_expiration_date IS NULL OR r.review_or_expiration_date >= :asOfDate)
              )
          )
          AND r.record_type IN (:recordTypes)
        ORDER BY bm25(knowledge_fts)
        LIMIT :limit
    """)
    suspend fun search(
        ftsQuery: String,
        specialty: String?,
        verifiedOnly: Boolean,
        recordTypes: List<String>,
        asOfDate: String,
        limit: Int
    ): List<KnowledgeSearchRow>

    @Query("SELECT * FROM knowledge_records WHERE stable_id = :id LIMIT 1")
    suspend fun get(id: String): KnowledgeEntity?

    @Query("SELECT * FROM knowledge_records WHERE review_status = 'UNREVIEWED' ORDER BY title COLLATE NOCASE LIMIT :limit")
    suspend fun unreviewed(limit: Int): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_records ORDER BY created_at_epoch_ms DESC LIMIT :limit")
    suspend fun listAll(limit: Int): List<KnowledgeEntity>

    @Query("UPDATE knowledge_records SET review_status = :status, reviewer_notes = :notes WHERE stable_id = :id")
    suspend fun updateReviewStatus(id: String, status: String, notes: String): Int

    @Query("DELETE FROM knowledge_records WHERE stable_id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface ModelRegistryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ModelRegistryEntity): Long

    @Query("SELECT * FROM model_registry ORDER BY imported_at_epoch_ms DESC")
    suspend fun list(): List<ModelRegistryEntity>

    @Query("SELECT * FROM model_registry WHERE model_id = :modelId AND model_version = :version LIMIT 1")
    suspend fun get(modelId: String, version: String): ModelRegistryEntity?

    @Query("UPDATE model_registry SET status = :status WHERE model_id = :modelId AND model_version = :version")
    suspend fun updateStatus(modelId: String, version: String, status: String): Int

    @Query("UPDATE model_registry SET status = 'REJECTED', rejection_reason = :reason WHERE model_id = :modelId AND model_version = :version")
    suspend fun reject(modelId: String, version: String, reason: String): Int

    @Query("SELECT status FROM model_registry WHERE model_id = :modelId AND model_version = :version LIMIT 1")
    suspend fun status(modelId: String, version: String): String?

    @Query("UPDATE model_registry SET is_active = 0, status = CASE WHEN status = 'ACTIVE' THEN 'SMOKE_TESTED' ELSE status END WHERE is_active = 1 OR status = 'ACTIVE'")
    suspend fun deactivateAll(): Int

    @Query("UPDATE model_registry SET is_active = 1, status = 'ACTIVE' WHERE model_id = :modelId AND model_version = :version AND status = 'SMOKE_TESTED'")
    suspend fun activateIfSmokeTested(modelId: String, version: String): Int

    @Transaction
    suspend fun activateExclusively(modelId: String, version: String): Boolean {
        if (status(modelId, version) !in setOf(ModelValidationState.SMOKE_TESTED.name, ModelValidationState.ACTIVE.name)) return false
        deactivateAll()
        return activateIfSmokeTested(modelId, version) == 1
    }
}

@Dao
interface TrainingDatasetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrainingDatasetEntity): Long

    @Query("SELECT * FROM training_datasets ORDER BY imported_at_epoch_ms DESC")
    suspend fun list(): List<TrainingDatasetEntity>

    @Query("SELECT * FROM training_datasets WHERE dataset_id = :id LIMIT 1")
    suspend fun get(id: String): TrainingDatasetEntity?

    @Query("DELETE FROM training_datasets WHERE dataset_id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface TrustedPublisherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrustedPublisherEntity): Long

    @Query("SELECT * FROM trusted_publishers ORDER BY display_name COLLATE NOCASE")
    suspend fun list(): List<TrustedPublisherEntity>

    @Query("SELECT * FROM trusted_publishers WHERE key_id = :keyId LIMIT 1")
    suspend fun get(keyId: String): TrustedPublisherEntity?

    @Query("DELETE FROM trusted_publishers WHERE key_id = :keyId")
    suspend fun delete(keyId: String): Int
}

@Database(
    entities = [
        KnowledgeEntity::class,
        KnowledgeFtsEntity::class,
        ModelRegistryEntity::class,
        TrustedPublisherEntity::class,
        TrainingDatasetEntity::class,
        EncryptedMemoryEntity::class,
        ConversationSessionEntity::class,
        EncryptedConversationMessageEntity::class,
        TrainingJobEntity::class,
        ToolPermissionDecisionEntity::class,
        AuditEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LocalMedDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun modelRegistryDao(): ModelRegistryDao
    abstract fun trustedPublisherDao(): TrustedPublisherDao
    abstract fun trainingDatasetDao(): TrainingDatasetDao

    companion object {
        fun create(context: Context): LocalMedDatabase = Room.databaseBuilder<LocalMedDatabase>(
            context.applicationContext,
            DATABASE_NAME
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        private const val DATABASE_NAME = "localmed_private.db"
    }
}

class RoomKnowledgeRepository(private val dao: KnowledgeDao) : KnowledgeRepository {
    override suspend fun search(query: KnowledgeQuery): List<KnowledgeHit> {
        val terms = FTS_TERM.findAll(query.text.take(1_000)).map { it.value }.distinct().take(16).toList()
        if (terms.isEmpty()) return emptyList()
        val ftsQuery = terms.joinToString(" OR ") { "\"$it\"" }
        val recordTypes = (query.recordTypes.ifEmpty { KnowledgeRecordType.entries.toSet() }).map { it.name }
        return dao.search(
            ftsQuery = ftsQuery,
            specialty = query.specialty?.trim()?.takeIf(String::isNotEmpty),
            verifiedOnly = query.verifiedOnly,
            recordTypes = recordTypes,
            asOfDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),
            limit = query.maxResults.coerceIn(1, 20)
        ).map { row ->
            KnowledgeHit(
                record = row.record.toDomain(),
                excerpt = makeExcerpt(row.record.content, terms),
                score = if (row.ftsRank.isFinite()) (-row.ftsRank).coerceAtLeast(0.0) else 0.0
            )
        }
    }

    override suspend fun get(id: String): KnowledgeRecord? = dao.get(id)?.toDomain()

    override suspend fun listUnreviewed(limit: Int): List<KnowledgeRecord> =
        dao.unreviewed(limit.coerceIn(1, 500)).map(KnowledgeEntity::toDomain)

    override suspend fun listAll(limit: Int): List<KnowledgeRecord> =
        dao.listAll(limit.coerceIn(1, 1_000)).map(KnowledgeEntity::toDomain)

    override suspend fun upsert(records: List<KnowledgeRecord>): Int {
        if (records.isEmpty()) return 0
        require(records.size <= MAX_IMPORT_RECORDS) { "Too many records for one database transaction." }
        records.forEach { record ->
            require(record.id.matches(STABLE_ID)) { "Stable record ID is invalid." }
        }
        return dao.insertAll(records.map { it.toEntity() }).size
    }

    override suspend fun setReviewStatus(id: String, status: ReviewStatus, reviewerNotes: String): Boolean =
        dao.updateReviewStatus(id, status.name, reviewerNotes.take(MAX_REVIEWER_NOTES)) > 0

    override suspend fun delete(id: String) { dao.delete(id) }

    private fun KnowledgeEntity.toDomain(): KnowledgeRecord {
        val proto = runCatching { KnowledgeRecordProto.parseFrom(canonicalProto) }.getOrNull()
        val recordType = runCatching { KnowledgeRecordType.valueOf(recordType) }.getOrDefault(KnowledgeRecordType.EDUCATIONAL_EXPLANATION)
        val evidence = runCatching { EvidenceLevel.valueOf(evidenceLevel) }.getOrDefault(EvidenceLevel.UNKNOWN)
        val status = runCatching { ReviewStatus.valueOf(reviewStatus) }.getOrDefault(ReviewStatus.UNREVIEWED)
        return KnowledgeRecord(
            id = proto?.stableId?.ifBlank { stableId } ?: stableId,
            recordType = recordType,
            specialty = proto?.specialty?.ifBlank { specialty } ?: specialty,
            title = proto?.title?.ifBlank { title } ?: title,
            content = proto?.content?.ifBlank { content } ?: content,
            source = proto?.source?.ifBlank { source } ?: source,
            sourceUrl = proto?.sourceUrl?.takeIf { it.isNotBlank() } ?: sourceUrl,
            authorOrOrganization = proto?.authorOrOrganization?.ifBlank { authorOrOrganization } ?: authorOrOrganization,
            publicationYear = proto?.publicationYear?.takeIf { it > 0 } ?: publicationYear,
            evidenceLevel = evidence,
            reviewStatus = status,
            revision = proto?.revision?.takeIf { it > 0 } ?: revision,
            effectiveDate = proto?.effectiveDate?.takeIf { it.isNotBlank() } ?: effectiveDate,
            reviewOrExpirationDate = proto?.reviewOrExpirationDate?.takeIf { it.isNotBlank() } ?: reviewOrExpirationDate,
            license = proto?.license?.ifBlank { license } ?: license,
            reviewerNotes = reviewerNotes,
            provenance = proto?.provenanceMap ?: emptyMap(),
            createdAt = Instant.ofEpochMilli(proto?.createdAtEpochMs?.takeIf { it > 0 } ?: createdAtEpochMs)
        )
    }

    private fun KnowledgeRecord.toEntity(): KnowledgeEntity {
        val proto = KnowledgeRecordProto.newBuilder()
            .setStableId(id)
            .setRecordType(recordType.name)
            .setSpecialty(specialty)
            .setTitle(title)
            .setContent(content)
            .setSource(source)
            .setSourceUrl(sourceUrl.orEmpty())
            .setAuthorOrOrganization(authorOrOrganization)
            .setPublicationYear(publicationYear ?: 0)
            .setEvidenceLevel(evidenceLevel.name)
            .setReviewStatus(reviewStatus.name)
            .setRevision(revision)
            .setEffectiveDate(effectiveDate.orEmpty())
            .setReviewOrExpirationDate(reviewOrExpirationDate.orEmpty())
            .setLicense(license)
            .setReviewerNotes(reviewerNotes)
            .putAllProvenance(provenance)
            .setCreatedAtEpochMs(createdAt.toEpochMilli())
            .build()
        return KnowledgeEntity(
            stableId = id,
            recordType = recordType.name,
            specialty = specialty,
            title = title,
            content = content,
            source = source,
            sourceUrl = sourceUrl,
            authorOrOrganization = authorOrOrganization,
            publicationYear = publicationYear,
            evidenceLevel = evidenceLevel.name,
            reviewStatus = reviewStatus.name,
            revision = revision,
            effectiveDate = effectiveDate,
            reviewOrExpirationDate = reviewOrExpirationDate,
            license = license,
            reviewerNotes = reviewerNotes,
            createdAtEpochMs = createdAt.toEpochMilli(),
            canonicalProto = proto.toByteArray()
        )
    }

    private fun makeExcerpt(content: String, terms: List<String>): String {
        val matchIndex = terms.asSequence().map { content.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (matchIndex - 240).coerceAtLeast(0)
        val end = (matchIndex + 800).coerceAtMost(content.length)
        return content.substring(start, end).trim().let { excerpt ->
            (if (start > 0) "…" else "") + excerpt + if (end < content.length) "…" else ""
        }
    }

    companion object {
        private const val MAX_IMPORT_RECORDS = 5_000
        private const val MAX_REVIEWER_NOTES = 4_000
        private val STABLE_ID = Regex("[A-Za-z0-9._:-]{1,128}")
        private val FTS_TERM = Regex("[\\p{L}\\p{N}]{2,}")
    }
}

class RoomModelRegistry(private val database: LocalMedDatabase) : ModelRegistry {
    private val dao get() = database.modelRegistryDao()

    override suspend fun list(): List<ModelRegistryEntry> = dao.list().map(ModelRegistryEntity::toDomain)
    override suspend fun get(modelId: String, version: String): ModelRegistryEntry? = dao.get(modelId, version)?.toDomain()

    override suspend fun register(entry: ModelRegistryEntry): Boolean {
        if (entry.modelId.isBlank() || entry.modelVersion.isBlank() || entry.manifestJson.length > MAX_MANIFEST_CHARS) return false
        return dao.insert(entry.toEntity()) >= 0
    }

    override suspend fun markSmokeTested(modelId: String, version: String): Boolean =
        dao.updateStatus(modelId, version, ModelValidationState.SMOKE_TESTED.name) > 0

    override suspend fun markRejected(modelId: String, version: String, reason: String): Boolean =
        dao.reject(modelId, version, reason.take(MAX_REJECTION_CHARS)) > 0

    override suspend fun activate(modelId: String, version: String): Boolean =
        dao.activateExclusively(modelId, version)

    override suspend fun deactivateAll() { dao.deactivateAll() }

    private fun ModelRegistryEntity.toDomain() = ModelRegistryEntry(
        modelId = modelId,
        modelVersion = modelVersion,
        manifestJson = manifestJson,
        status = runCatching { ModelValidationState.valueOf(status) }.getOrDefault(ModelValidationState.REJECTED),
        importedAt = Instant.ofEpochMilli(importedAtEpochMs),
        modelSha256 = modelSha256,
        tokenizerSha256 = tokenizerSha256,
        isActive = isActive
    )

    private fun ModelRegistryEntry.toEntity() = ModelRegistryEntity(
        modelId = modelId,
        modelVersion = modelVersion,
        manifestJson = manifestJson,
        status = status.name,
        importedAtEpochMs = importedAt.toEpochMilli(),
        modelSha256 = modelSha256,
        tokenizerSha256 = tokenizerSha256,
        isActive = isActive
    )

    companion object {
        private const val MAX_MANIFEST_CHARS = 100_000
        private const val MAX_REJECTION_CHARS = 500
    }
}

class RoomTrainingDatasetRepository(private val dao: TrainingDatasetDao) : TrainingDatasetRepository {
    override suspend fun list(): List<TrainingDatasetRecord> = dao.list().map(TrainingDatasetEntity::toDomain)

    override suspend fun register(record: TrainingDatasetRecord): Boolean {
        if (!record.id.matches(Regex("[A-Za-z0-9._-]{1,128}")) || !com.localmed.core.common.Hashing.isSha256(record.sha256)) return false
        if (record.byteSize !in 1..(50L * 1024 * 1024) || record.exampleCount !in 1..10_000) return false
        if (record.displayName.isBlank() || record.source.isBlank() || record.license.isBlank()) return false
        dao.insert(record.toEntity())
        return true
    }

    override suspend fun remove(id: String): Boolean = dao.delete(id) > 0

    private fun TrainingDatasetEntity.toDomain() = TrainingDatasetRecord(
        id = datasetId,
        displayName = displayName,
        sha256 = sha256,
        byteSize = byteSize,
        exampleCount = exampleCount,
        importedAt = Instant.ofEpochMilli(importedAtEpochMs),
        source = source,
        license = license
    )

    private fun TrainingDatasetRecord.toEntity() = TrainingDatasetEntity(
        datasetId = id,
        displayName = displayName,
        sha256 = sha256,
        byteSize = byteSize,
        exampleCount = exampleCount,
        importedAtEpochMs = importedAt.toEpochMilli(),
        source = source,
        license = license
    )
}

class RoomTrustedPublisherRepository(private val dao: TrustedPublisherDao) : TrustedPublisherRepository {
    override suspend fun listTrustedPublishers(): List<TrustedPublisher> = dao.list().map(TrustedPublisherEntity::toDomain)
    override suspend fun addTrustedPublisher(publisher: TrustedPublisher): Boolean {
        if (publisher.keyId.isBlank() || publisher.publicKeyX509Base64.length > 8_000 || publisher.displayName.isBlank()) return false
        dao.insert(publisher.toEntity())
        return true
    }
    override suspend fun removeTrustedPublisher(keyId: String) { dao.delete(keyId) }
    override suspend fun getTrustedPublisher(keyId: String): TrustedPublisher? = dao.get(keyId)?.toDomain()

    private fun TrustedPublisherEntity.toDomain() = TrustedPublisher(keyId, publicKeyX509Base64, fingerprintSha256, displayName, trustGrantedAtEpochMs)
    private fun TrustedPublisher.toEntity() = TrustedPublisherEntity(keyId, publicKeyX509Base64, fingerprintSha256, displayName, trustGrantedAtEpochMs)
}
