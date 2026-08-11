package com.example.llm.asset.processing.repository;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.index.EmbeddingRuntime;
import com.example.llm.auth.security.AuthCrypto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AssetIndexRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuthCrypto crypto;
    private final AssetProcessingProperties properties;
    private final EmbeddingRuntime embeddingRuntime;

    public AssetIndexRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            AuthCrypto crypto,
            AssetProcessingProperties properties,
            EmbeddingRuntime embeddingRuntime) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.crypto = crypto;
        this.properties = properties;
        this.embeddingRuntime = embeddingRuntime;
    }

    public int reconcileMissingWork(LocalDateTime scheduledAt) {
        if (!embeddingRuntime.isSemanticIndexAvailable()) {
            return 0;
        }
        return transactions.execute(status -> {
            Optional<ModelDefinition> model = findActiveModel();
            if (model.isEmpty()) {
                return 0;
            }
            ModelDefinition activeModel = model.get();
            List<ChunkCandidate> chunks = jdbc.query("""
                    SELECT c.id, c.external_id, a.user_id
                      FROM document_chunk c
                      JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                      JOIN asset_version av ON av.id = pr.asset_version_id
                      JOIN asset a ON a.id = av.asset_id
                     WHERE pr.status = 'READY'
                       AND av.status = 'READY'
                       AND a.status IN ('ACTIVE', 'ARCHIVED')
                       AND NOT EXISTS (
                           SELECT 1
                             FROM embedding_record er
                            WHERE er.chunk_id = c.id
                              AND er.model_definition_id = ?
                              AND er.embedding_version = ?
                       )
                     ORDER BY c.id ASC
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                    """, (rs, rowNum) -> new ChunkCandidate(
                    rs.getLong("id"), rs.getString("external_id"), rs.getLong("user_id")),
                    activeModel.id(), properties.getIndexing().getEmbeddingVersion(),
                    properties.getIndexing().getReconcileBatchSize());
            for (ChunkCandidate chunk : chunks) {
                createIndexWork(chunk, activeModel, scheduledAt);
            }
            return chunks.size();
        });
    }

    public Optional<IndexTarget> prepareIndex(String chunkExternalId) {
        return transactions.execute(status -> {
            List<IndexTarget> targets = jdbc.query("""
                    SELECT er.id AS embedding_record_id, er.status AS embedding_status,
                           er.index_document_id, c.external_id AS chunk_external_id,
                           c.content, c.content_sha256, c.sequence_no,
                           pr.id AS parse_result_id,
                           av.id AS asset_version_id, a.id AS asset_id, a.user_id AS owner_user_id
                      FROM document_chunk c
                      JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                      JOIN asset_version av ON av.id = pr.asset_version_id
                      JOIN asset a ON a.id = av.asset_id
                      JOIN embedding_record er ON er.chunk_id = c.id
                      JOIN model_definition md ON md.id = er.model_definition_id
                      JOIN model_provider mp ON mp.id = md.provider_id
                     WHERE c.external_id = ?
                       AND md.model_key = ?
                       AND md.role = 'EMBEDDING'
                       AND md.status = 'ACTIVE'
                       AND mp.status = 'ACTIVE'
                       AND er.embedding_version = ?
                       AND er.index_name = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new IndexTarget(
                    rs.getLong("embedding_record_id"), rs.getString("embedding_status"),
                    rs.getString("index_document_id"), rs.getString("chunk_external_id"),
                    rs.getString("content"), rs.getString("content_sha256"), rs.getInt("sequence_no"),
                    rs.getLong("parse_result_id"), rs.getLong("asset_version_id"),
                    rs.getLong("asset_id"), rs.getLong("owner_user_id")),
                    chunkExternalId,
                    properties.getIndexing().getModelKey(),
                    properties.getIndexing().getEmbeddingVersion(),
                    properties.getIndexing().getIndexName());
            if (targets.isEmpty()) {
                return Optional.empty();
            }
            IndexTarget target = targets.get(0);
            if (!target.embeddingStatus().equals("INDEXED")) {
                jdbc.update("""
                        UPDATE embedding_record
                           SET status = 'INDEXING', indexed_at = NULL, deleted_at = NULL,
                               row_version = row_version + 1
                         WHERE id = ? AND status IN ('PENDING', 'FAILED', 'INDEXING')
                        """, target.embeddingRecordId());
                target = target.withStatus("INDEXING");
            }
            return Optional.of(target);
        });
    }

    public void completeIndex(long embeddingRecordId, LocalDateTime indexedAt) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE embedding_record
                   SET status = 'INDEXED', indexed_at = ?, deleted_at = NULL,
                       row_version = row_version + 1
                 WHERE id = ? AND status IN ('PENDING', 'INDEXING', 'FAILED')
                """, indexedAt, embeddingRecordId));
    }

    public void failIndex(String chunkExternalId) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE embedding_record er
                JOIN document_chunk c ON c.id = er.chunk_id
                JOIN model_definition md ON md.id = er.model_definition_id
                   SET er.status = 'FAILED', er.indexed_at = NULL, er.deleted_at = NULL,
                       er.row_version = er.row_version + 1
                 WHERE c.external_id = ?
                   AND md.model_key = ?
                   AND er.embedding_version = ?
                   AND er.index_name = ?
                   AND er.status IN ('PENDING', 'INDEXING', 'FAILED')
                """, chunkExternalId,
                properties.getIndexing().getModelKey(),
                properties.getIndexing().getEmbeddingVersion(),
                properties.getIndexing().getIndexName()));
    }

    private Optional<ModelDefinition> findActiveModel() {
        List<ModelDefinition> models = jdbc.query("""
                SELECT md.id,
                       CAST(JSON_UNQUOTE(JSON_EXTRACT(md.capability_json, '$.dimensions')) AS UNSIGNED)
                           AS dimensions
                  FROM model_definition md
                  JOIN model_provider mp ON mp.id = md.provider_id
                 WHERE md.model_key = ?
                   AND md.role = 'EMBEDDING'
                   AND md.status = 'ACTIVE'
                   AND mp.status = 'ACTIVE'
                """, (rs, rowNum) -> new ModelDefinition(
                rs.getLong("id"), rs.getInt("dimensions")),
                properties.getIndexing().getModelKey());
        if (models.isEmpty()) {
            return Optional.empty();
        }
        ModelDefinition model = models.get(0);
        if (model.dimensions() != properties.getIndexing().getDimensions()) {
            throw new IllegalStateException("Configured embedding dimensions do not match the V2 model registry");
        }
        return Optional.of(model);
    }

    private void createIndexWork(
            ChunkCandidate chunk,
            ModelDefinition model,
            LocalDateTime scheduledAt) {
        String embeddingExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO embedding_record (
                    external_id, chunk_id, model_definition_id, embedding_version,
                    index_name, index_document_id, content_sha256, status,
                    indexed_at, deleted_at
                )
                SELECT ?, c.id, ?, ?, ?, ?, c.content_sha256, 'PENDING', NULL, NULL
                  FROM document_chunk c
                 WHERE c.id = ?
                """, embeddingExternalId, model.id(), properties.getIndexing().getEmbeddingVersion(),
                properties.getIndexing().getIndexName(), embeddingExternalId, chunk.id());

        String idempotencyKey = crypto.digest("chunk-index-job", embeddingExternalId);
        jdbc.update("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, priority,
                    idempotency_scope, idempotency_key, cancellable, payload_json,
                    result_json, scheduled_at, started_at, heartbeat_at, finished_at,
                    error_code, safe_error_message, lease_owner, lease_expires_at,
                    attempt_count, max_attempts
                ) VALUES (?, ?, 'FILE_INDEX', 'DOCUMENT_CHUNK', ?,
                          'QUEUED', 'VECTOR_INDEX', 0, 1, 110,
                          'embedding-record', ?, FALSE,
                          JSON_OBJECT('embeddingRecordId', ?, 'chunkId', ?,
                                      'embeddingVersion', ?, 'indexName', ?),
                          NULL, ?, NULL, NULL, NULL,
                          NULL, NULL, NULL, NULL, 0, 5)
                """, crypto.newExternalId(), chunk.userId(), chunk.externalId(), idempotencyKey,
                embeddingExternalId, chunk.externalId(), properties.getIndexing().getEmbeddingVersion(),
                properties.getIndexing().getIndexName(), scheduledAt);
    }

    private record ModelDefinition(long id, int dimensions) {
    }

    private record ChunkCandidate(long id, String externalId, long userId) {
    }

    public record IndexTarget(
            long embeddingRecordId,
            String embeddingStatus,
            String indexDocumentId,
            String chunkExternalId,
            String content,
            String contentSha256,
            int sequenceNo,
            long parseResultId,
            long assetVersionId,
            long assetId,
            long ownerUserId) {
        public IndexTarget withStatus(String status) {
            return new IndexTarget(
                    embeddingRecordId, status, indexDocumentId, chunkExternalId,
                    content, contentSha256, sequenceNo, parseResultId,
                    assetVersionId, assetId, ownerUserId);
        }
    }
}
