package com.example.llm.asset.processing.repository;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.parse.AssetContentExtractor.ExtractedContent;
import com.example.llm.asset.processing.parse.StructuredTextChunker.TextChunk;
import com.example.llm.auth.security.AuthCrypto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Repository
public class AssetProcessingRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuthCrypto crypto;
    private final AssetProcessingProperties properties;

    public AssetProcessingRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            AuthCrypto crypto,
            AssetProcessingProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.crypto = crypto;
        this.properties = properties;
    }

    public Optional<StorageTarget> prepareSecurityScan(
            String storageExternalId,
            String scannerKey,
            String scannerVersion) {
        return transactions.execute(status -> {
            Optional<StorageTarget> target = findStorageForUpdate(storageExternalId);
            if (target.isEmpty()) {
                return Optional.empty();
            }
            StorageTarget current = target.get();
            if (current.status().equals("AVAILABLE") || current.status().equals("REJECTED")) {
                return target;
            }
            if (!current.status().equals("QUARANTINED") && !current.status().equals("SCANNING")) {
                throw new IllegalStateException("Storage object cannot be scanned from " + current.status());
            }
            jdbc.update("""
                    UPDATE storage_object
                       SET status = 'SCANNING', scanner_key = ?, scanner_version = ?,
                           scan_completed_at = NULL, safe_rejection_code = NULL,
                           row_version = row_version + 1
                     WHERE id = ?
                    """, scannerKey, scannerVersion, current.id());
            return Optional.of(new StorageTarget(
                    current.id(), current.externalId(), "SCANNING", current.objectKeyCiphertext(),
                    current.mimeType(), current.sizeBytes()));
        });
    }

    public void resetSecurityScan(String storageExternalId) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE storage_object
                   SET status = 'QUARANTINED', scanner_key = NULL, scanner_version = NULL,
                       scan_completed_at = NULL, safe_rejection_code = NULL,
                       row_version = row_version + 1
                 WHERE external_id = ? AND status = 'SCANNING'
                """, storageExternalId));
    }

    public void failSecurityScan(String storageExternalId) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE storage_object
                       SET status = 'QUARANTINED', scanner_key = NULL, scanner_version = NULL,
                           scan_completed_at = NULL, safe_rejection_code = NULL,
                           row_version = row_version + 1
                     WHERE external_id = ? AND status = 'SCANNING'
                    """, storageExternalId);
            jdbc.update("""
                    UPDATE asset_version version
                    JOIN storage_object storage ON storage.id = version.storage_object_id
                       SET version.status = 'FAILED',
                           version.row_version = version.row_version + 1
                     WHERE storage.external_id = ?
                       AND version.status = 'QUARANTINED'
                    """, storageExternalId);
        });
    }

    public List<String> completeCleanScan(String storageExternalId, LocalDateTime completedAt) {
        return transactions.execute(status -> {
            StorageTarget storage = findStorageForUpdate(storageExternalId)
                    .orElseThrow(() -> new IllegalStateException("Storage object not found"));
            if (storage.status().equals("REJECTED")) {
                return List.of();
            }
            if (!storage.status().equals("AVAILABLE")) {
                jdbc.update("""
                        UPDATE storage_object
                           SET status = 'AVAILABLE', scan_completed_at = ?, safe_rejection_code = NULL,
                               row_version = row_version + 1
                         WHERE id = ? AND status = 'SCANNING'
                        """, completedAt, storage.id());
            }
            List<AssetVersionTarget> versions = findVersionsForUpdate(storage.id());
            for (AssetVersionTarget version : versions) {
                if (version.status().equals("REJECTED") || version.status().equals("WITHDRAWN")) {
                    continue;
                }
                if (version.status().equals("QUARANTINED")) {
                    jdbc.update("""
                            UPDATE asset_version
                               SET status = 'PROCESSING', row_version = row_version + 1
                             WHERE id = ? AND status = 'QUARANTINED'
                            """, version.id());
                }
                ensureParseJob(version, completedAt);
            }
            return versions.stream().map(AssetVersionTarget::externalId).toList();
        });
    }

    public void completeRejectedScan(String storageExternalId, LocalDateTime completedAt) {
        transactions.executeWithoutResult(status -> {
            StorageTarget storage = findStorageForUpdate(storageExternalId)
                    .orElseThrow(() -> new IllegalStateException("Storage object not found"));
            if (storage.status().equals("REJECTED")) {
                return;
            }
            jdbc.update("""
                    UPDATE storage_object
                       SET status = 'REJECTED', scan_completed_at = ?,
                           safe_rejection_code = 'MALWARE_DETECTED',
                           row_version = row_version + 1
                     WHERE id = ? AND status = 'SCANNING'
                    """, completedAt, storage.id());
            jdbc.update("""
                    UPDATE asset_version
                       SET status = 'REJECTED', active_parse_result_id = NULL,
                           row_version = row_version + 1
                     WHERE storage_object_id = ?
                       AND status IN ('QUARANTINED', 'PROCESSING')
                    """, storage.id());
        });
    }

    public Optional<ParseTarget> prepareParse(long jobId, String versionExternalId) {
        return transactions.execute(status -> {
            List<ParseTarget> targets = jdbc.query("""
                    SELECT pr.id AS parse_result_id, pr.status AS parse_status,
                           av.id AS version_id, av.asset_id, av.status AS version_status,
                           av.mime_type, av.size_bytes,
                           so.status AS storage_status, so.object_key_ciphertext
                      FROM asset_version av
                      JOIN storage_object so ON so.id = av.storage_object_id
                      JOIN asset_parse_result pr ON pr.asset_version_id = av.id
                     WHERE av.external_id = ? AND pr.async_job_id = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new ParseTarget(
                    rs.getLong("parse_result_id"), rs.getString("parse_status"),
                    rs.getLong("version_id"), rs.getLong("asset_id"), rs.getString("version_status"),
                    rs.getString("mime_type"), rs.getLong("size_bytes"),
                    rs.getString("storage_status"), rs.getBytes("object_key_ciphertext")),
                    versionExternalId, jobId);
            if (targets.isEmpty()) {
                return Optional.empty();
            }
            ParseTarget target = targets.get(0);
            if (target.parseStatus().equals("READY") || target.parseStatus().equals("FAILED")) {
                return Optional.of(target);
            }
            if (!target.storageStatus().equals("AVAILABLE")) {
                throw new IllegalStateException("Storage object is not available for parsing");
            }
            jdbc.update("""
                    UPDATE asset_parse_result
                       SET status = 'PROCESSING', row_version = row_version + 1
                     WHERE id = ? AND status = 'QUEUED'
                    """, target.parseResultId());
            return Optional.of(new ParseTarget(
                    target.parseResultId(), "PROCESSING", target.versionId(), target.assetId(),
                    target.versionStatus(), target.mimeType(), target.sizeBytes(),
                    target.storageStatus(), target.objectKeyCiphertext()));
        });
    }

    public int completeParse(
            long parseResultId,
            long versionId,
            long assetId,
            ExtractedContent extracted,
            List<TextChunk> chunks,
            LocalDateTime completedAt) {
        return transactions.execute(status -> {
            String parseStatus = jdbc.queryForObject(
                    "SELECT status FROM asset_parse_result WHERE id = ? FOR UPDATE",
                    String.class,
                    parseResultId);
            if ("READY".equals(parseStatus)) {
                Integer count = jdbc.queryForObject(
                        "SELECT chunk_count FROM asset_parse_result WHERE id = ?", Integer.class, parseResultId);
                return count == null ? 0 : count;
            }
            if (!"PROCESSING".equals(parseStatus) && !"QUEUED".equals(parseStatus)) {
                throw new IllegalStateException("Parse result cannot be completed from " + parseStatus);
            }
            jdbc.update("DELETE FROM document_chunk WHERE parse_result_id = ?", parseResultId);
            for (TextChunk chunk : chunks) {
                jdbc.update("""
                        INSERT INTO document_chunk (
                            external_id, parse_result_id, sequence_no, content,
                            content_sha256, token_count, page_from, page_to,
                            locator_json, heading_path
                        ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL)
                        """, crypto.newExternalId(), parseResultId, chunk.sequence(), chunk.content(),
                        sha256(chunk.content()), chunk.tokenCount());
            }
            jdbc.update("""
                    UPDATE asset_parse_result
                       SET status = 'READY', language = ?, page_count = ?, chunk_count = ?,
                           plain_text_sha256 = ?, safe_error_code = NULL, completed_at = ?,
                           row_version = row_version + 1
                     WHERE id = ?
                    """, extracted.language(), extracted.pageCount(), chunks.size(),
                    sha256(extracted.text()), completedAt, parseResultId);
            jdbc.update("""
                    UPDATE asset_version
                       SET status = 'READY', active_parse_result_id = ?,
                           rag_status = CASE
                               WHEN rag_policy = 'AUTO' THEN 'PENDING'
                               WHEN rag_policy = 'DISABLED' THEN 'DISABLED'
                               ELSE 'NOT_INDEXED'
                           END,
                           rag_error_code = NULL,
                           indexed_at = NULL,
                           row_version = row_version + 1
                     WHERE id = ? AND status = 'PROCESSING'
                    """, parseResultId, versionId);
            jdbc.update("""
                    UPDATE asset
                       SET current_version_id = ?, row_version = row_version + 1
                     WHERE id = ? AND current_version_id IS NULL AND status = 'ACTIVE'
                    """, versionId, assetId);
            return chunks.size();
        });
    }

    public void failParse(long jobId, String safeErrorCode, LocalDateTime completedAt) {
        transactions.executeWithoutResult(status -> {
            List<Long> versionIds = jdbc.query("""
                    SELECT asset_version_id
                      FROM asset_parse_result
                     WHERE async_job_id = ? AND status IN ('QUEUED', 'PROCESSING')
                     FOR UPDATE
                    """, (rs, rowNum) -> rs.getLong(1), jobId);
            jdbc.update("""
                    UPDATE asset_parse_result
                       SET status = 'FAILED', chunk_count = 0, plain_text_sha256 = NULL,
                           safe_error_code = ?, completed_at = ?, row_version = row_version + 1
                     WHERE async_job_id = ? AND status IN ('QUEUED', 'PROCESSING')
                    """, safeErrorCode, completedAt, jobId);
            for (Long versionId : versionIds) {
                jdbc.update("""
                        UPDATE asset_version
                           SET status = 'FAILED', active_parse_result_id = NULL,
                               rag_status = CASE
                                   WHEN rag_policy = 'DISABLED' THEN 'DISABLED'
                                   ELSE 'FAILED'
                               END,
                               rag_error_code = ?, indexed_at = NULL,
                               row_version = row_version + 1
                         WHERE id = ? AND status = 'PROCESSING'
                        """, safeErrorCode, versionId);
            }
        });
    }

    public int reconcileObsoleteOcrFailures(LocalDateTime scheduledAt) {
        return transactions.execute(status -> {
            String parserKey = properties.getParser().getKey();
            String parserVersion = properties.getParser().getVersion();
            List<AssetVersionTarget> versions = jdbc.query("""
                    SELECT av.id, av.external_id, av.created_by_user_id, av.status
                      FROM asset_version av
                      JOIN asset a ON a.id = av.asset_id
                      JOIN storage_object so ON so.id = av.storage_object_id
                     WHERE av.status = 'FAILED'
                       AND av.mime_type LIKE 'image/%'
                       AND a.status IN ('ACTIVE', 'ARCHIVED')
                       AND so.status = 'AVAILABLE'
                       AND EXISTS (
                           SELECT 1
                             FROM asset_parse_result failed
                            WHERE failed.asset_version_id = av.id
                              AND failed.safe_error_code = 'OCR_UNAVAILABLE'
                       )
                       AND NOT EXISTS (
                           SELECT 1
                             FROM asset_parse_result current_parser
                            WHERE current_parser.asset_version_id = av.id
                              AND current_parser.parser_key = ?
                              AND current_parser.parser_version = ?
                       )
                     ORDER BY av.id ASC
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                    """, (rs, rowNum) -> new AssetVersionTarget(
                    rs.getLong("id"), rs.getString("external_id"),
                    rs.getLong("created_by_user_id"), rs.getString("status")),
                    parserKey, parserVersion, properties.getBatchSize());
            for (AssetVersionTarget version : versions) {
                jdbc.update("""
                        UPDATE asset_version
                           SET status = 'PROCESSING', active_parse_result_id = NULL,
                               row_version = row_version + 1
                         WHERE id = ? AND status = 'FAILED'
                        """, version.id());
                ensureParseJob(version, scheduledAt);
            }
            return versions.size();
        });
    }

    /**
     * Reopens the existing terminal parse/index work instead of inserting a second job. This keeps
     * the original idempotency key authoritative while preserving the previous attempt audit rows.
     */
    public RetryOutcome retryFailedWork(long userId, String assetExternalId, LocalDateTime scheduledAt) {
        return transactions.execute(status -> {
            List<RetryVersion> versions = jdbc.query("""
                    SELECT a.status AS asset_status,
                           av.id AS version_id, av.external_id AS version_external_id,
                           av.status AS version_status, av.active_parse_result_id,
                           av.rag_policy, av.rag_status
                      FROM asset a
                      JOIN asset_version av ON av.id = COALESCE(
                          a.current_version_id,
                          (SELECT latest.id FROM asset_version latest
                            WHERE latest.asset_id = a.id
                            ORDER BY latest.version_no DESC LIMIT 1)
                      )
                     WHERE a.user_id = ? AND a.external_id = ? AND a.status <> 'PURGED'
                     FOR UPDATE
                    """, (rs, rowNum) -> new RetryVersion(
                    rs.getString("asset_status"), rs.getLong("version_id"),
                    rs.getString("version_external_id"), rs.getString("version_status"),
                    rs.getObject("active_parse_result_id", Long.class),
                    rs.getString("rag_policy"), rs.getString("rag_status")),
                    userId, assetExternalId);
            if (versions.isEmpty()) {
                return RetryOutcome.NOT_FOUND;
            }
            RetryVersion version = versions.get(0);
            if (!"ACTIVE".equals(version.assetStatus())) {
                return RetryOutcome.ASSET_NOT_ACTIVE;
            }
            if ("FAILED".equals(version.versionStatus())) {
                return retryParse(version, scheduledAt);
            }
            if ("READY".equals(version.versionStatus()) && "AUTO".equals(version.ragPolicy())) {
                return retryIndex(version, scheduledAt);
            }
            if ("QUARANTINED".equals(version.versionStatus())
                    || "PROCESSING".equals(version.versionStatus())) {
                return RetryOutcome.ALREADY_PROCESSING;
            }
            return RetryOutcome.NOTHING_TO_RETRY;
        });
    }

    private RetryOutcome retryParse(RetryVersion version, LocalDateTime scheduledAt) {
        List<RetryParse> parseResults = jdbc.query("""
                SELECT id, async_job_id, status
                  FROM asset_parse_result
                 WHERE asset_version_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                 FOR UPDATE
                """, (rs, rowNum) -> new RetryParse(
                rs.getLong("id"), rs.getLong("async_job_id"), rs.getString("status")),
                version.versionId());
        if (parseResults.isEmpty() || !"FAILED".equals(parseResults.get(0).status())) {
            return RetryOutcome.NOTHING_TO_RETRY;
        }
        RetryParse parse = parseResults.get(0);
        String jobStatus = jdbc.queryForObject(
                "SELECT status FROM async_job WHERE id = ? FOR UPDATE", String.class, parse.jobId());
        if (isActiveJob(jobStatus)) {
            return RetryOutcome.ALREADY_PROCESSING;
        }
        if (!"FAILED".equals(jobStatus)) {
            return RetryOutcome.NOTHING_TO_RETRY;
        }
        reopenJob(parse.jobId(), "TEXT_EXTRACTION", scheduledAt);
        jdbc.update("""
                UPDATE asset_parse_result
                   SET status = 'QUEUED', chunk_count = 0, plain_text_sha256 = NULL,
                       safe_error_code = NULL, completed_at = NULL,
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'FAILED'
                """, parse.id());
        jdbc.update("""
                UPDATE asset_version
                   SET status = 'PROCESSING', active_parse_result_id = NULL,
                       rag_status = CASE
                           WHEN rag_policy = 'AUTO' THEN 'PENDING'
                           WHEN rag_policy = 'DISABLED' THEN 'DISABLED'
                           ELSE 'NOT_INDEXED'
                       END,
                       rag_error_code = NULL, indexed_at = NULL,
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'FAILED'
                """, version.versionId());
        return RetryOutcome.PARSE_REQUEUED;
    }

    private RetryOutcome retryIndex(RetryVersion version, LocalDateTime scheduledAt) {
        if (!"FAILED".equals(version.ragStatus())) {
            return "PENDING".equals(version.ragStatus()) || "INDEXING".equals(version.ragStatus())
                    ? RetryOutcome.ALREADY_PROCESSING
                    : RetryOutcome.NOTHING_TO_RETRY;
        }
        List<Long> failedEmbeddingIds = jdbc.query("""
                SELECT er.id
                  FROM embedding_record er
                  JOIN document_chunk chunk ON chunk.id = er.chunk_id
                  JOIN model_definition model ON model.id = er.model_definition_id
                 WHERE chunk.parse_result_id = ?
                   AND er.embedding_version = ?
                   AND er.index_name = ?
                   AND model.model_key = ?
                   AND er.status = 'FAILED'
                 FOR UPDATE
                """, (rs, rowNum) -> rs.getLong(1), version.activeParseResultId(),
                properties.getIndexing().getEmbeddingVersion(),
                properties.getIndexing().getIndexName(),
                properties.getIndexing().getModelKey());
        if (failedEmbeddingIds.isEmpty()) {
            return RetryOutcome.NOTHING_TO_RETRY;
        }
        int activeJobs = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM async_job job
                  JOIN document_chunk chunk ON chunk.external_id = job.aggregate_external_id
                  JOIN embedding_record er ON er.chunk_id = chunk.id
                 WHERE er.id IN (%s)
                   AND job.job_type = 'FILE_INDEX'
                   AND job.status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                """.formatted(placeholders(failedEmbeddingIds.size())), Integer.class,
                failedEmbeddingIds.toArray());
        if (activeJobs > 0) {
            return RetryOutcome.ALREADY_PROCESSING;
        }
        jdbc.update("""
                UPDATE embedding_record
                   SET status = 'PENDING', indexed_at = NULL, deleted_at = NULL,
                       row_version = row_version + 1
                 WHERE id IN (%s) AND status = 'FAILED'
                """.formatted(placeholders(failedEmbeddingIds.size())), failedEmbeddingIds.toArray());
        jdbc.update("""
                UPDATE async_job job
                JOIN document_chunk chunk ON chunk.external_id = job.aggregate_external_id
                JOIN embedding_record er ON er.chunk_id = chunk.id
                   SET job.status = 'RETRY_WAIT', job.stage_key = 'VECTOR_INDEXING',
                       job.progress_current = 0, job.progress_total = 1,
                       job.scheduled_at = ?, job.started_at = NULL, job.heartbeat_at = NULL,
                       job.finished_at = NULL, job.error_code = NULL,
                       job.safe_error_message = NULL, job.lease_owner = NULL,
                       job.lease_expires_at = NULL,
                       job.max_attempts = GREATEST(job.max_attempts, job.attempt_count + 3),
                       job.row_version = job.row_version + 1
                 WHERE er.id IN (%s)
                   AND job.job_type = 'FILE_INDEX' AND job.status = 'FAILED'
                """.formatted(placeholders(failedEmbeddingIds.size())),
                prepend(scheduledAt, failedEmbeddingIds));
        jdbc.update("""
                UPDATE asset_version
                   SET rag_status = 'INDEXING', rag_error_code = NULL, indexed_at = NULL,
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'READY' AND rag_policy = 'AUTO'
                """, version.versionId());
        return RetryOutcome.INDEX_REQUEUED;
    }

    private void reopenJob(long jobId, String stageKey, LocalDateTime scheduledAt) {
        jdbc.update("""
                UPDATE async_job
                   SET status = 'RETRY_WAIT', stage_key = ?, progress_current = 0,
                       progress_total = 1, scheduled_at = ?, started_at = NULL,
                       heartbeat_at = NULL, finished_at = NULL, error_code = NULL,
                       safe_error_message = NULL, lease_owner = NULL, lease_expires_at = NULL,
                       max_attempts = GREATEST(max_attempts, attempt_count + 3),
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'FAILED'
                """, stageKey, scheduledAt, jobId);
    }

    private boolean isActiveJob(String status) {
        return "QUEUED".equals(status) || "RUNNING".equals(status) || "RETRY_WAIT".equals(status);
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private Object[] prepend(Object first, List<Long> values) {
        Object[] args = new Object[values.size() + 1];
        args[0] = first;
        for (int index = 0; index < values.size(); index++) {
            args[index + 1] = values.get(index);
        }
        return args;
    }

    private Optional<StorageTarget> findStorageForUpdate(String storageExternalId) {
        return jdbc.query("""
                SELECT id, external_id, status, object_key_ciphertext, mime_type, size_bytes
                  FROM storage_object
                 WHERE external_id = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new StorageTarget(
                rs.getLong("id"), rs.getString("external_id"), rs.getString("status"),
                rs.getBytes("object_key_ciphertext"), rs.getString("mime_type"), rs.getLong("size_bytes")),
                storageExternalId).stream().findFirst();
    }

    private List<AssetVersionTarget> findVersionsForUpdate(long storageObjectId) {
        return jdbc.query("""
                SELECT id, external_id, created_by_user_id, status
                  FROM asset_version
                 WHERE storage_object_id = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new AssetVersionTarget(
                rs.getLong("id"), rs.getString("external_id"),
                rs.getLong("created_by_user_id"), rs.getString("status")), storageObjectId);
    }

    private void ensureParseJob(AssetVersionTarget version, LocalDateTime scheduledAt) {
        String parserKey = properties.getParser().getKey();
        String parserVersion = properties.getParser().getVersion();
        String idempotencyKey = crypto.digest(
                "asset-parse-job", version.externalId() + "\0" + parserKey + "\0" + parserVersion);
        String jobExternalId = crypto.newExternalId();
        jdbc.update("""
                INSERT IGNORE INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, priority,
                    idempotency_scope, idempotency_key, cancellable, payload_json,
                    result_json, scheduled_at, started_at, heartbeat_at, finished_at,
                    error_code, safe_error_message, lease_owner, lease_expires_at,
                    attempt_count, max_attempts
                ) VALUES (?, ?, 'FILE_PARSE', 'ASSET_VERSION', ?,
                          'QUEUED', 'TEXT_EXTRACTION', 0, 1, 100,
                          'asset-version-parser', ?, FALSE,
                          JSON_OBJECT('assetVersionId', ?, 'parserKey', ?, 'parserVersion', ?),
                          NULL, ?, NULL, NULL, NULL,
                          NULL, NULL, NULL, NULL, 0, 3)
                """, jobExternalId, version.userId(), version.externalId(), idempotencyKey,
                version.externalId(), parserKey, parserVersion, scheduledAt);
        Long jobId = jdbc.queryForObject("""
                SELECT id FROM async_job
                 WHERE idempotency_scope = 'asset-version-parser'
                   AND job_type = 'FILE_PARSE'
                   AND idempotency_key = ?
                """, Long.class, idempotencyKey);
        jdbc.update("""
                INSERT IGNORE INTO asset_parse_result (
                    external_id, asset_version_id, parser_key, parser_version, async_job_id,
                    status, language, page_count, chunk_count, plain_text_sha256,
                    safe_error_code, completed_at
                ) VALUES (?, ?, ?, ?, ?, 'QUEUED', NULL, NULL, 0, NULL, NULL, NULL)
                """, crypto.newExternalId(), version.id(), parserKey, parserVersion, jobId);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash parsed text", exception);
        }
    }

    public record StorageTarget(
            long id,
            String externalId,
            String status,
            byte[] objectKeyCiphertext,
            String mimeType,
            long sizeBytes) {
    }

    private record AssetVersionTarget(long id, String externalId, long userId, String status) {
    }

    private record RetryVersion(
            String assetStatus,
            long versionId,
            String versionExternalId,
            String versionStatus,
            Long activeParseResultId,
            String ragPolicy,
            String ragStatus) {
    }

    private record RetryParse(long id, long jobId, String status) {
    }

    public enum RetryOutcome {
        PARSE_REQUEUED,
        INDEX_REQUEUED,
        ALREADY_PROCESSING,
        NOTHING_TO_RETRY,
        ASSET_NOT_ACTIVE,
        NOT_FOUND
    }

    public record ParseTarget(
            long parseResultId,
            String parseStatus,
            long versionId,
            long assetId,
            String versionStatus,
            String mimeType,
            long sizeBytes,
            String storageStatus,
            byte[] objectKeyCiphertext) {
    }
}
