package com.example.llm.asset.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class UploadRepository {
    private static final RowMapper<UploadSession> UPLOAD_SESSION_MAPPER = (rs, rowNum) ->
            new UploadSession(
                    rs.getLong("id"),
                    rs.getString("external_id"),
                    rs.getLong("user_id"),
                    rs.getString("upload_key"),
                    rs.getString("original_filename"),
                    rs.getString("declared_mime"),
                    rs.getLong("expected_size"),
                    rs.getString("expected_sha256"),
                    rs.getString("status"),
                    rs.getInt("part_size"),
                    rs.getLong("uploaded_bytes"),
                    rs.getObject("expires_at", LocalDateTime.class),
                    rs.getObject("completed_at", LocalDateTime.class),
                    rs.getLong("row_version"));

    private static final RowMapper<CompletionRecord> COMPLETION_MAPPER = (rs, rowNum) ->
            new CompletionRecord(
                    rs.getString("upload_external_id"),
                    rs.getString("upload_status"),
                    rs.getString("asset_external_id"),
                    rs.getString("asset_name"),
                    rs.getString("asset_status"),
                    rs.getString("version_external_id"),
                    rs.getInt("version_no"),
                    rs.getString("version_status"),
                    rs.getString("mime_type"),
                    rs.getLong("size_bytes"),
                    rs.getString("content_sha256"),
                    rs.getString("job_external_id"),
                    rs.getString("job_status"),
                    rs.getString("stage_key"));

    private final JdbcTemplate jdbc;

    public UploadRepository(@Qualifier("v2JdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> lockUserStatus(long userId) {
        return jdbc.query(
                "SELECT status FROM app_user WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getString("status"),
                userId).stream().findFirst();
    }

    public void expireStaleUploads(long userId, LocalDateTime now) {
        jdbc.update("""
                UPDATE upload_session
                   SET status = 'EXPIRED', row_version = row_version + 1
                 WHERE user_id = ?
                   AND status IN ('INITIATED', 'UPLOADING', 'COMPLETING')
                   AND expires_at <= ?
                """, userId, now);
    }

    public int countConcurrentUploads(long userId, LocalDateTime now) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM upload_session
                 WHERE user_id = ?
                   AND status IN ('INITIATED', 'UPLOADING', 'COMPLETING')
                   AND expires_at > ?
                """, Integer.class, userId, now);
        return count == null ? 0 : count;
    }

    public Optional<UploadSession> findByUserAndUploadKey(long userId, String uploadKey) {
        return querySession("""
                SELECT id, external_id, user_id, upload_key, original_filename, declared_mime,
                       expected_size, expected_sha256, status, part_size, uploaded_bytes,
                       expires_at, completed_at, row_version
                  FROM upload_session
                 WHERE user_id = ? AND upload_key = ?
                """, userId, uploadKey);
    }

    public Optional<UploadSession> findByExternalIdForUpdate(long userId, String uploadExternalId) {
        return querySession("""
                SELECT id, external_id, user_id, upload_key, original_filename, declared_mime,
                       expected_size, expected_sha256, status, part_size, uploaded_bytes,
                       expires_at, completed_at, row_version
                  FROM upload_session
                 WHERE user_id = ? AND external_id = ?
                 FOR UPDATE
                """, userId, uploadExternalId);
    }

    public long insertUploadSession(
            String externalId,
            long userId,
            String uploadKey,
            String originalFilename,
            String declaredMime,
            long expectedSize,
            String expectedSha256,
            int partSize,
            LocalDateTime expiresAt) {
        return insertAndReturnId("""
                INSERT INTO upload_session (
                    external_id, user_id, upload_key, original_filename, declared_mime,
                    expected_size, expected_sha256, multipart_upload_ref_ciphertext,
                    status, part_size, uploaded_bytes, expires_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'INITIATED', ?, 0, ?, NULL)
                """, externalId, userId, uploadKey, originalFilename, declaredMime,
                expectedSize, expectedSha256, partSize, expiresAt);
    }

    public void updateProgress(long uploadId, long uploadedBytes) {
        jdbc.update("""
                UPDATE upload_session
                   SET status = 'UPLOADING', uploaded_bytes = ?, row_version = row_version + 1
                 WHERE id = ? AND status IN ('INITIATED', 'UPLOADING')
                """, uploadedBytes, uploadId);
    }

    public void markCompleting(long uploadId) {
        jdbc.update("""
                UPDATE upload_session
                   SET status = 'COMPLETING', row_version = row_version + 1
                 WHERE id = ? AND status IN ('INITIATED', 'UPLOADING')
                """, uploadId);
    }

    public void markTerminal(long uploadId, String status) {
        jdbc.update("""
                UPDATE upload_session
                   SET status = ?, completed_at = NULL, row_version = row_version + 1
                 WHERE id = ? AND status <> 'COMPLETED'
                """, status, uploadId);
    }

    public void markCompleted(long uploadId, LocalDateTime completedAt) {
        jdbc.update("""
                UPDATE upload_session
                   SET status = 'COMPLETED',
                       completed_at = GREATEST(created_at, ?),
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'COMPLETING'
                """, completedAt, uploadId);
    }

    public long insertStorageObject(
            String externalId,
            long ownerUserId,
            String bucketKey,
            byte[] objectKeyCiphertext,
            String objectKeyHash,
            String sha256,
            long sizeBytes,
            String mimeType) {
        return insertAndReturnId("""
                INSERT INTO storage_object (
                    external_id, owner_user_id, bucket_key, object_key_ciphertext,
                    object_key_hash, sha256, size_bytes, mime_type, encryption_key_ref,
                    status, verified_at, scanner_key, scanner_version, scan_completed_at,
                    safe_rejection_code, purged_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL,
                          'QUARANTINED', CURRENT_TIMESTAMP(3), NULL, NULL, NULL, NULL, NULL)
                """, externalId, ownerUserId, bucketKey, objectKeyCiphertext,
                objectKeyHash, sha256, sizeBytes, mimeType);
    }

    public long insertAsset(String externalId, long userId, String name) {
        return insertAndReturnId("""
                INSERT INTO asset (
                    external_id, user_id, name, asset_type, source_type,
                    current_version_id, status, trash_started_at, previous_status, deleted_at
                ) VALUES (?, ?, ?, 'FILE', 'UPLOAD', NULL, 'ACTIVE', NULL, NULL, NULL)
                """, externalId, userId, name);
    }

    public long insertAssetVersion(
            String externalId,
            long assetId,
            long uploadSessionId,
            long storageObjectId,
            String sha256,
            String mimeType,
            long sizeBytes,
            long userId) {
        return insertAndReturnId("""
                INSERT INTO asset_version (
                    external_id, asset_id, version_no, upload_session_id, storage_object_id,
                    text_content, content_sha256, mime_type, size_bytes, source_type, status,
                    active_parse_result_id, generated_by_ai, ai_run_id, generation_label,
                    created_by_user_id
                ) VALUES (?, ?, 1, ?, ?, NULL, ?, ?, ?, 'UPLOAD', 'QUARANTINED',
                          NULL, FALSE, NULL, NULL, ?)
                """, externalId, assetId, uploadSessionId, storageObjectId,
                sha256, mimeType, sizeBytes, userId);
    }

    public long insertSecurityScanJob(
            String externalId,
            long userId,
            String storageObjectExternalId,
            String payloadJson,
            LocalDateTime scheduledAt) {
        return insertAndReturnId("""
                INSERT INTO async_job (
                    external_id, user_id, job_type, aggregate_type, aggregate_external_id,
                    status, stage_key, progress_current, progress_total, priority,
                    idempotency_scope, idempotency_key, cancellable, payload_json,
                    result_json, scheduled_at, started_at, heartbeat_at, finished_at,
                    error_code, safe_error_message, lease_owner, lease_expires_at,
                    attempt_count, max_attempts
                ) VALUES (?, ?, 'FILE_SECURITY_SCAN', 'STORAGE_OBJECT', ?,
                          'QUEUED', 'SECURITY_SCAN', 0, 1, 100,
                          'storage-object', ?, FALSE, CAST(? AS JSON),
                          NULL, ?, NULL, NULL, NULL,
                          NULL, NULL, NULL, NULL, 0, 10)
                """, externalId, userId, storageObjectExternalId, storageObjectExternalId,
                payloadJson, scheduledAt);
    }

    public Optional<CompletionRecord> findCompletion(long userId, String uploadExternalId) {
        List<CompletionRecord> records = jdbc.query("""
                SELECT us.external_id AS upload_external_id,
                       us.status AS upload_status,
                       a.external_id AS asset_external_id,
                       a.name AS asset_name,
                       a.status AS asset_status,
                       av.external_id AS version_external_id,
                       av.version_no,
                       av.status AS version_status,
                       av.mime_type,
                       av.size_bytes,
                       av.content_sha256,
                       j.external_id AS job_external_id,
                       j.status AS job_status,
                       j.stage_key
                  FROM upload_session us
                  JOIN asset_version av ON av.upload_session_id = us.id
                  JOIN asset a ON a.id = av.asset_id
                  JOIN storage_object so ON so.id = av.storage_object_id
                  JOIN async_job j
                    ON j.job_type = 'FILE_SECURITY_SCAN'
                   AND j.aggregate_type = 'STORAGE_OBJECT'
                   AND j.aggregate_external_id = so.external_id
                 WHERE us.user_id = ? AND us.external_id = ?
                """, COMPLETION_MAPPER, userId, uploadExternalId);
        return records.stream().findFirst();
    }

    private Optional<UploadSession> querySession(String sql, Object... arguments) {
        return jdbc.query(sql, UPLOAD_SESSION_MAPPER, arguments).stream().findFirst();
    }

    private long insertAndReturnId(String sql, Object... arguments) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey(), "Database did not return a generated key").longValue();
    }

    public record UploadSession(
            long id,
            String externalId,
            long userId,
            String uploadKey,
            String originalFilename,
            String declaredMime,
            long expectedSize,
            String expectedSha256,
            String status,
            int partSize,
            long uploadedBytes,
            LocalDateTime expiresAt,
            LocalDateTime completedAt,
            long rowVersion) {
    }

    public record CompletionRecord(
            String uploadExternalId,
            String uploadStatus,
            String assetExternalId,
            String assetName,
            String assetStatus,
            String versionExternalId,
            int versionNumber,
            String versionStatus,
            String mimeType,
            long sizeBytes,
            String sha256,
            String jobExternalId,
            String jobStatus,
            String stageKey) {
    }
}
