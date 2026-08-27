package com.example.llm.asset.repository;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AssetLibraryRepository {
    private static final String ASSET_SELECT = """
            SELECT a.id, a.external_id, a.name, a.asset_type, a.source_type, a.status,
                   a.trash_started_at, a.created_at, a.updated_at,
                   av.external_id AS version_external_id, av.version_no, av.status AS version_status,
                   av.mime_type, av.size_bytes, av.created_at AS version_created_at,
                   COALESCE((
                       SELECT COUNT(*) FROM document_chunk c
                        WHERE c.parse_result_id = av.active_parse_result_id
                   ), 0) AS chunk_count,
                   COALESCE((
                       SELECT COUNT(*)
                         FROM embedding_record er
                         JOIN document_chunk c ON c.id = er.chunk_id
                        WHERE c.parse_result_id = av.active_parse_result_id
                          AND er.embedding_version = ?
                          AND er.index_name = ?
                          AND er.status = 'INDEXED'
                   ), 0) AS indexed_chunk_count,
                   COALESCE((
                       SELECT COUNT(*)
                         FROM embedding_record er
                         JOIN document_chunk c ON c.id = er.chunk_id
                        WHERE c.parse_result_id = av.active_parse_result_id
                          AND er.embedding_version = ?
                          AND er.index_name = ?
                          AND er.status = 'FAILED'
                   ), 0) AS failed_chunk_count,
                   COALESCE((
                       SELECT COUNT(*)
                         FROM knowledge_base_asset membership
                         JOIN knowledge_base kb ON kb.id = membership.knowledge_base_id
                        WHERE membership.asset_id = a.id AND kb.status = 'ACTIVE'
                   ), 0) AS knowledge_base_count
              FROM asset a
              LEFT JOIN asset_version av ON av.id = COALESCE(
                  a.current_version_id,
                  (SELECT latest.id FROM asset_version latest
                    WHERE latest.asset_id = a.id
                    ORDER BY latest.version_no DESC LIMIT 1)
              )
            """;

    private final JdbcTemplate jdbc;
    private final AssetProcessingProperties processingProperties;

    public AssetLibraryRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            AssetProcessingProperties processingProperties) {
        this.jdbc = jdbc;
        this.processingProperties = processingProperties;
    }

    public List<AssetRow> findPage(
            long userId,
            String status,
            PageCursor cursor,
            int fetchLimit) {
        StringBuilder sql = new StringBuilder(ASSET_SELECT)
                .append(" WHERE a.user_id = ? AND a.status = ?");
        List<Object> args = baseArgs();
        args.add(userId);
        args.add(status);
        if (cursor != null) {
            sql.append(" AND (a.updated_at < ? OR (a.updated_at = ? AND a.id < ?))");
            args.add(cursor.updatedAt());
            args.add(cursor.updatedAt());
            args.add(cursor.id());
        }
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ?");
        args.add(fetchLimit);
        return jdbc.query(sql.toString(), this::mapAsset, args.toArray());
    }

    public Optional<AssetRow> findByExternalId(long userId, String assetExternalId) {
        List<Object> args = baseArgs();
        args.add(userId);
        args.add(assetExternalId);
        List<AssetRow> rows = jdbc.query(
                ASSET_SELECT + " WHERE a.user_id = ? AND a.external_id = ? AND a.status <> 'PURGED'",
                this::mapAsset,
                args.toArray());
        return rows.stream().findFirst();
    }

    public Optional<ReadableAssetContent> findReadableContent(
            long userId,
            String assetExternalId) {
        List<ReadableAssetContent> rows = jdbc.query("""
                SELECT a.name, av.mime_type, av.size_bytes, so.object_key_ciphertext
                  FROM asset a
                  JOIN asset_version av ON av.id = COALESCE(
                      a.current_version_id,
                      (SELECT candidate.id
                         FROM asset_version candidate
                         JOIN storage_object candidate_storage
                           ON candidate_storage.id = candidate.storage_object_id
                        WHERE candidate.asset_id = a.id
                          AND candidate.status IN ('PROCESSING', 'READY', 'FAILED')
                          AND candidate_storage.status = 'AVAILABLE'
                        ORDER BY candidate.version_no DESC
                        LIMIT 1)
                  )
                  JOIN storage_object so ON so.id = av.storage_object_id
                 WHERE a.user_id = ? AND a.external_id = ?
                   AND a.status = 'ACTIVE'
                   AND av.status IN ('PROCESSING', 'READY', 'FAILED')
                   AND so.status = 'AVAILABLE'
                """, (rs, rowNum) -> new ReadableAssetContent(
                rs.getString("name"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getBytes("object_key_ciphertext")), userId, assetExternalId);
        return rows.stream().findFirst();
    }

    public List<AssetRow> findKnowledgeBaseAssets(
            long userId,
            long knowledgeBaseId,
            PageCursor cursor,
            int fetchLimit) {
        StringBuilder sql = new StringBuilder(ASSET_SELECT)
                .append(" JOIN knowledge_base_asset membership ON membership.asset_id = a.id")
                .append(" WHERE membership.knowledge_base_id = ?")
                .append(" AND membership.added_by_user_id = ?")
                .append(" AND a.user_id = ? AND a.status = 'ACTIVE'");
        List<Object> args = baseArgs();
        args.add(knowledgeBaseId);
        args.add(userId);
        args.add(userId);
        if (cursor != null) {
            sql.append(" AND (a.updated_at < ? OR (a.updated_at = ? AND a.id < ?))");
            args.add(cursor.updatedAt());
            args.add(cursor.updatedAt());
            args.add(cursor.id());
        }
        sql.append(" ORDER BY a.updated_at DESC, a.id DESC LIMIT ?");
        args.add(fetchLimit);
        return jdbc.query(sql.toString(), this::mapAsset, args.toArray());
    }

    public List<KnowledgeBaseReferenceRow> findActiveKnowledgeBases(long userId, long assetId) {
        return jdbc.query("""
                SELECT kb.external_id, kb.name
                  FROM knowledge_base_asset membership
                  JOIN knowledge_base kb ON kb.id = membership.knowledge_base_id
                 WHERE membership.asset_id = ?
                   AND membership.added_by_user_id = ?
                   AND kb.user_id = ?
                   AND kb.status = 'ACTIVE'
                 ORDER BY kb.name ASC, kb.id ASC
                """, (rs, rowNum) -> new KnowledgeBaseReferenceRow(
                rs.getString(1), rs.getString(2)), assetId, userId, userId);
    }

    public Optional<AssetLifecycle> findForUpdate(long userId, String assetExternalId) {
        List<AssetLifecycle> rows = jdbc.query("""
                SELECT id, external_id, name, status, previous_status, trash_started_at,
                       deleted_at, row_version
                  FROM asset
                 WHERE user_id = ? AND external_id = ? AND status <> 'PURGED'
                 FOR UPDATE
                """, (rs, rowNum) -> new AssetLifecycle(
                rs.getLong("id"), rs.getString("external_id"), rs.getString("name"),
                rs.getString("status"), rs.getString("previous_status"),
                rs.getObject("trash_started_at", LocalDateTime.class),
                rs.getObject("deleted_at", LocalDateTime.class),
                rs.getLong("row_version")), userId, assetExternalId);
        return rows.stream().findFirst();
    }

    /**
     * Returns a bounded batch of assets whose recoverable-trash window has
     * elapsed.  The caller must re-read each row under a transaction before
     * scheduling a purge because a user may restore it between this scan and
     * the enqueue operation.
     */
    public List<ExpiredTrashAsset> findExpiredTrash(LocalDateTime cutoff, int limit) {
        return jdbc.query("""
                SELECT id, user_id, external_id, trash_started_at
                  FROM asset
                 WHERE status = 'TRASHED'
                   AND trash_started_at IS NOT NULL
                   AND trash_started_at <= ?
                 ORDER BY trash_started_at ASC, id ASC
                 LIMIT ?
                """, (rs, rowNum) -> new ExpiredTrashAsset(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("external_id"),
                rs.getObject("trash_started_at", LocalDateTime.class)), cutoff, limit);
    }

    public void rename(long assetId, String name) {
        jdbc.update("""
                UPDATE asset
                   SET name = ?, row_version = row_version + 1
                 WHERE id = ? AND status <> 'PURGED'
                """, name, assetId);
    }

    public void moveToTrash(long assetId, String previousStatus, LocalDateTime now) {
        jdbc.update("""
                UPDATE asset
                   SET status = 'TRASHED', previous_status = ?, trash_started_at = ?,
                       deleted_at = ?, row_version = row_version + 1
                 WHERE id = ? AND status IN ('ACTIVE', 'ARCHIVED')
                """, previousStatus, now, now, assetId);
    }

    public void restore(long assetId) {
        jdbc.update("""
                UPDATE asset
                   SET status = 'ACTIVE', previous_status = NULL, trash_started_at = NULL,
                       deleted_at = NULL, row_version = row_version + 1
                 WHERE id = ? AND status = 'TRASHED'
                """, assetId);
    }

    public Optional<PurgeJobRow> findLatestPurgeJob(long userId, String assetExternalId) {
        List<PurgeJobRow> rows = jdbc.query("""
                SELECT external_id, status, error_code, created_at, finished_at
                  FROM async_job
                 WHERE user_id = ? AND job_type = 'ASSET_PURGE'
                   AND aggregate_type = 'ASSET' AND aggregate_external_id = ?
                 ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> new PurgeJobRow(
                rs.getString("external_id"), rs.getString("status"), rs.getString("error_code"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("finished_at", LocalDateTime.class)), userId, assetExternalId);
        return rows.stream().findFirst();
    }

    public boolean hasActivePurgeJob(long userId, String assetExternalId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM async_job
                     WHERE user_id = ? AND job_type = 'ASSET_PURGE'
                       AND aggregate_type = 'ASSET' AND aggregate_external_id = ?
                       AND status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'CANCELLING')
                )
                """, Boolean.class, userId, assetExternalId);
        return Boolean.TRUE.equals(exists);
    }

    public PurgeJobRow enqueuePurge(
            String jobExternalId,
            long userId,
            String assetExternalId,
            String idempotencyKey,
            LocalDateTime now) {
        jdbc.update("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, priority,
                    idempotency_scope, idempotency_key, cancellable, payload_json,
                    result_json, scheduled_at, started_at, heartbeat_at, finished_at,
                    error_code, safe_error_message, lease_owner, lease_expires_at,
                    attempt_count, max_attempts
                ) VALUES (?, ?, 'ASSET_PURGE', 'ASSET', ?,
                          'QUEUED', 'ASSET_PURGE', 0, 1, 120,
                          'asset-purge', ?, FALSE, JSON_OBJECT('assetId', ?),
                          NULL, ?, NULL, NULL, NULL,
                          NULL, NULL, NULL, NULL, 0, 5)
                """, jobExternalId, userId, assetExternalId, idempotencyKey, assetExternalId, now);
        return findLatestPurgeJob(userId, assetExternalId)
                .orElseThrow(() -> new IllegalStateException("Created purge job cannot be loaded"));
    }

    private List<Object> baseArgs() {
        List<Object> args = new ArrayList<>();
        args.add(processingProperties.getIndexing().getEmbeddingVersion());
        args.add(processingProperties.getIndexing().getIndexName());
        args.add(processingProperties.getIndexing().getEmbeddingVersion());
        args.add(processingProperties.getIndexing().getIndexName());
        return args;
    }

    AssetRow mapAsset(ResultSet rs, int rowNum) throws SQLException {
        String versionExternalId = rs.getString("version_external_id");
        VersionRow version = versionExternalId == null ? null : new VersionRow(
                versionExternalId,
                rs.getInt("version_no"),
                rs.getString("version_status"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getInt("chunk_count"),
                rs.getInt("indexed_chunk_count"),
                rs.getInt("failed_chunk_count"),
                rs.getObject("version_created_at", LocalDateTime.class));
        return new AssetRow(
                rs.getLong("id"),
                rs.getString("external_id"),
                rs.getString("name"),
                rs.getString("asset_type"),
                rs.getString("source_type"),
                rs.getString("status"),
                rs.getInt("knowledge_base_count"),
                version,
                rs.getObject("trash_started_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    public record PageCursor(LocalDateTime updatedAt, long id) {
    }

    public record AssetRow(
            long id,
            String externalId,
            String name,
            String assetType,
            String sourceType,
            String status,
            int knowledgeBaseCount,
            VersionRow version,
            LocalDateTime trashStartedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record VersionRow(
            String externalId,
            int versionNumber,
            String status,
            String mimeType,
            long sizeBytes,
            int chunkCount,
            int indexedChunkCount,
            int failedChunkCount,
            LocalDateTime createdAt) {
    }

    public record AssetLifecycle(
            long id,
            String externalId,
            String name,
            String status,
            String previousStatus,
            LocalDateTime trashStartedAt,
            LocalDateTime deletedAt,
            long rowVersion) {
    }

    public record ExpiredTrashAsset(
            long id,
            long userId,
            String externalId,
            LocalDateTime trashStartedAt) {
    }

    public record KnowledgeBaseReferenceRow(String externalId, String name) {
    }

    public record PurgeJobRow(
            String externalId,
            String status,
            String errorCode,
            LocalDateTime createdAt,
            LocalDateTime finishedAt) {
    }

    public record ReadableAssetContent(
            String name,
            String mimeType,
            long sizeBytes,
            byte[] encryptedObjectKey) {
    }
}
