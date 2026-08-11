package com.example.llm.chatv2.artifact;

import com.example.llm.chatv2.artifact.ArtifactModels.ArtifactView;
import com.example.llm.chatv2.artifact.ArtifactModels.Type;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ArtifactDraftRepository {
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<ArtifactRow> mapper;

    public ArtifactDraftRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, ignored) -> new ArtifactRow(
                rs.getLong("id"),
                rs.getString("external_id"),
                rs.getLong("user_id"),
                rs.getLong("conversation_id"),
                rs.getString("conversation_external_id"),
                nullableLong(rs, "ai_run_id"),
                rs.getString("run_external_id"),
                nullableLong(rs, "request_message_id"),
                Type.valueOf(rs.getString("artifact_type")),
                rs.getString("status"),
                rs.getString("title"),
                rs.getInt("schema_version"),
                readJson(rs.getString("content_json")),
                rs.getInt("current_revision_no"),
                nullableLong(rs, "confirmed_asset_version_id"),
                rs.getString("confirmed_asset_version_external_id"),
                rs.getString("confirmed_asset_external_id"),
                rs.getString("error_code"),
                rs.getLong("row_version"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("confirmed_at")));
    }

    public long insertDraft(
            String externalId,
            long userId,
            long conversationId,
            Long aiRunId,
            Long requestMessageId,
            Type type,
            String status,
            String title,
            String contentJson) {
        return insertAndReturnId("""
                INSERT INTO artifact_draft (
                    external_id, user_id, conversation_id, ai_run_id, request_message_id,
                    artifact_type, status, title, schema_version, content_json,
                    current_revision_no, confirmed_asset_version_id, error_code,
                    row_version, confirmed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, CAST(? AS JSON),
                          1, NULL, NULL, 0, NULL)
                """, externalId, userId, conversationId, aiRunId, requestMessageId,
                type.name(), status, title, contentJson);
    }

    public void insertRevision(
            long draftId,
            int revision,
            String origin,
            String contentJson,
            String contentHash,
            Long createdByUserId) {
        jdbc.update("""
                INSERT INTO artifact_revision (
                    artifact_draft_id, revision_no, origin, content_json,
                    content_hash, created_by_user_id
                ) VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)
                """, draftId, revision, origin, contentJson, contentHash, createdByUserId);
    }

    public Optional<ArtifactRow> findOwned(long userId, String externalId, boolean forUpdate) {
        String locking = forUpdate ? " FOR UPDATE" : "";
        return jdbc.query("""
                SELECT d.id, d.external_id, d.user_id, d.conversation_id,
                       c.external_id AS conversation_external_id,
                       d.ai_run_id, r.external_id AS run_external_id,
                       d.request_message_id, d.artifact_type, d.status, d.title,
                       d.schema_version, CAST(d.content_json AS CHAR) AS content_json,
                       d.current_revision_no, d.confirmed_asset_version_id,
                       av.external_id AS confirmed_asset_version_external_id,
                       a.external_id AS confirmed_asset_external_id,
                       d.error_code, d.row_version, d.created_at, d.updated_at, d.confirmed_at
                  FROM artifact_draft d
                  JOIN conversation c ON c.id = d.conversation_id
                  LEFT JOIN ai_run r ON r.id = d.ai_run_id
                  LEFT JOIN asset_version av ON av.id = d.confirmed_asset_version_id
                  LEFT JOIN asset a ON a.id = av.asset_id
                 WHERE d.user_id = ? AND d.external_id = ?
                """ + locking, mapper, userId, externalId).stream().findFirst();
    }

    public List<ArtifactRow> findOwnedByConversation(long userId, String conversationExternalId) {
        return jdbc.query("""
                SELECT d.id, d.external_id, d.user_id, d.conversation_id,
                       c.external_id AS conversation_external_id,
                       d.ai_run_id, r.external_id AS run_external_id,
                       d.request_message_id, d.artifact_type, d.status, d.title,
                       d.schema_version, CAST(d.content_json AS CHAR) AS content_json,
                       d.current_revision_no, d.confirmed_asset_version_id,
                       av.external_id AS confirmed_asset_version_external_id,
                       a.external_id AS confirmed_asset_external_id,
                       d.error_code, d.row_version, d.created_at, d.updated_at, d.confirmed_at
                  FROM artifact_draft d
                  JOIN conversation c ON c.id = d.conversation_id
                  LEFT JOIN ai_run r ON r.id = d.ai_run_id
                  LEFT JOIN asset_version av ON av.id = d.confirmed_asset_version_id
                  LEFT JOIN asset a ON a.id = av.asset_id
                 WHERE d.user_id = ? AND c.external_id = ?
                 ORDER BY d.created_at ASC, d.id ASC
                """, mapper, userId, conversationExternalId);
    }

    public void updateDraft(
            long draftId,
            String title,
            String contentJson,
            int revision,
            long expectedVersion) {
        int updated = jdbc.update("""
                UPDATE artifact_draft
                   SET title = ?, content_json = CAST(? AS JSON), current_revision_no = ?,
                       row_version = row_version + 1
                 WHERE id = ? AND row_version = ? AND status IN ('DRAFT', 'READY')
                """, title, contentJson, revision, draftId, expectedVersion);
        if (updated != 1) {
            throw new OptimisticArtifactUpdateException();
        }
    }

    public void markReady(long draftId) {
        jdbc.update("""
                UPDATE artifact_draft
                   SET status = 'READY', error_code = NULL, row_version = row_version + 1
                 WHERE id = ? AND status IN ('GENERATING', 'DRAFT')
                """, draftId);
    }

    public void markFailed(long draftId, String errorCode) {
        jdbc.update("""
                UPDATE artifact_draft
                   SET status = 'FAILED', error_code = ?, row_version = row_version + 1
                 WHERE id = ? AND status <> 'CONFIRMED'
                """, errorCode, draftId);
    }

    public void markConfirmed(long draftId, long assetVersionId) {
        int updated = jdbc.update("""
                UPDATE artifact_draft
                   SET status = 'CONFIRMED', confirmed_asset_version_id = ?,
                       confirmed_at = CURRENT_TIMESTAMP(3), error_code = NULL,
                       row_version = row_version + 1
                 WHERE id = ? AND status IN ('DRAFT', 'READY', 'GENERATING')
                   AND confirmed_asset_version_id IS NULL
                """, assetVersionId, draftId);
        if (updated != 1) {
            throw new OptimisticArtifactUpdateException();
        }
    }

    public ArtifactView view(ArtifactRow row) {
        return new ArtifactView(
                row.externalId(), row.conversationExternalId(), row.runExternalId(), row.type(),
                row.status(), row.title(), row.schemaVersion(), row.content(), row.currentRevision(),
                row.rowVersion(), row.confirmedAssetExternalId(), row.confirmedAssetVersionExternalId(),
                row.errorCode(), row.createdAt(), row.updatedAt(), row.confirmedAt());
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

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, JSON_MAP);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read artifact JSON", exception);
        }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record ArtifactRow(
            long id,
            String externalId,
            long userId,
            long conversationId,
            String conversationExternalId,
            Long aiRunId,
            String runExternalId,
            Long requestMessageId,
            Type type,
            String status,
            String title,
            int schemaVersion,
            Map<String, Object> content,
            int currentRevision,
            Long confirmedAssetVersionId,
            String confirmedAssetVersionExternalId,
            String confirmedAssetExternalId,
            String errorCode,
            long rowVersion,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt) {
    }

    public static final class OptimisticArtifactUpdateException extends RuntimeException {
    }
}
