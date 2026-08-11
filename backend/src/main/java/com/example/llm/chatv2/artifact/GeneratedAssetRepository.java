package com.example.llm.chatv2.artifact;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;

@Repository
public class GeneratedAssetRepository {
    private final JdbcTemplate jdbc;

    public GeneratedAssetRepository(@Qualifier("v2JdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertAvailableStorageObject(
            String externalId,
            long userId,
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
                          'AVAILABLE', CURRENT_TIMESTAMP(3), 'server-generated', '1',
                          CURRENT_TIMESTAMP(3), NULL, NULL)
                """, externalId, userId, bucketKey, objectKeyCiphertext, objectKeyHash,
                sha256, sizeBytes, mimeType);
    }

    public long insertGeneratedAsset(String externalId, long userId, String name) {
        return insertAndReturnId("""
                INSERT INTO asset (
                    external_id, user_id, name, asset_type, source_type,
                    current_version_id, status, trash_started_at, previous_status, deleted_at
                ) VALUES (?, ?, ?, 'FILE', 'AI_GENERATED', NULL, 'ACTIVE', NULL, NULL, NULL)
                """, externalId, userId, name);
    }

    public GeneratedVersion insertGeneratedVersion(
            String externalId,
            long assetId,
            long storageObjectId,
            String sha256,
            String mimeType,
            long sizeBytes,
            Long aiRunId,
            String generationLabel,
            long userId) {
        long id = insertAndReturnId("""
                INSERT INTO asset_version (
                    external_id, asset_id, version_no, upload_session_id, storage_object_id,
                    text_content, content_sha256, mime_type, size_bytes, source_type, status,
                    active_parse_result_id, generated_by_ai, ai_run_id, generation_label,
                    rag_policy, rag_status, rag_error_code, indexed_at, created_by_user_id
                ) VALUES (?, ?, 1, NULL, ?, NULL, ?, ?, ?, 'AI_GENERATED', 'READY',
                          NULL, TRUE, ?, ?, 'MANUAL', 'NOT_INDEXED', NULL, NULL, ?)
                """, externalId, assetId, storageObjectId, sha256, mimeType, sizeBytes,
                aiRunId, generationLabel, userId);
        jdbc.update("UPDATE asset SET current_version_id = ? WHERE id = ?", id, assetId);
        return new GeneratedVersion(id, externalId);
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

    public record GeneratedVersion(long id, String externalId) {
    }
}
