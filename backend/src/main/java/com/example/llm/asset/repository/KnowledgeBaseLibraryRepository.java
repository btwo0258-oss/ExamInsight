package com.example.llm.asset.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeBaseLibraryRepository {
    private final JdbcTemplate jdbc;

    public KnowledgeBaseLibraryRepository(@Qualifier("v2JdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(
            String externalId,
            long userId,
            String name,
            String normalizedName,
            String description) {
        jdbc.update("""
                INSERT INTO knowledge_base (
                    external_id, user_id, name, normalized_name, description, status
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, externalId, userId, name, normalizedName, description);
        return jdbc.queryForObject(
                "SELECT id FROM knowledge_base WHERE external_id = ?",
                Long.class,
                externalId);
    }

    public List<KnowledgeBaseRow> findPage(
            long userId,
            String status,
            PageCursor cursor,
            int fetchLimit) {
        StringBuilder sql = new StringBuilder("""
                SELECT kb.id, kb.external_id, kb.name, kb.normalized_name, kb.description,
                       kb.status, kb.trash_started_at, kb.created_at, kb.updated_at,
                       COALESCE((
                           SELECT COUNT(*)
                             FROM knowledge_base_asset membership
                             JOIN asset a ON a.id = membership.asset_id
                            WHERE membership.knowledge_base_id = kb.id AND a.status = 'ACTIVE'
                       ), 0) AS asset_count
                  FROM knowledge_base kb
                 WHERE kb.user_id = ? AND kb.status = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(status);
        if (cursor != null) {
            sql.append(" AND (kb.updated_at < ? OR (kb.updated_at = ? AND kb.id < ?))");
            args.add(cursor.updatedAt());
            args.add(cursor.updatedAt());
            args.add(cursor.id());
        }
        sql.append(" ORDER BY kb.updated_at DESC, kb.id DESC LIMIT ?");
        args.add(fetchLimit);
        return jdbc.query(sql.toString(), (rs, rowNum) -> new KnowledgeBaseRow(
                rs.getLong("id"), rs.getString("external_id"), rs.getString("name"),
                rs.getString("normalized_name"), rs.getString("description"),
                rs.getString("status"), rs.getInt("asset_count"),
                rs.getObject("trash_started_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)), args.toArray());
    }

    public Optional<KnowledgeBaseRow> findByExternalId(long userId, String externalId) {
        List<KnowledgeBaseRow> rows = jdbc.query("""
                SELECT kb.id, kb.external_id, kb.name, kb.normalized_name, kb.description,
                       kb.status, kb.trash_started_at, kb.created_at, kb.updated_at,
                       COALESCE((
                           SELECT COUNT(*)
                             FROM knowledge_base_asset membership
                             JOIN asset a ON a.id = membership.asset_id
                            WHERE membership.knowledge_base_id = kb.id AND a.status = 'ACTIVE'
                       ), 0) AS asset_count
                  FROM knowledge_base kb
                 WHERE kb.user_id = ? AND kb.external_id = ? AND kb.status <> 'PURGED'
                """, (rs, rowNum) -> new KnowledgeBaseRow(
                rs.getLong("id"), rs.getString("external_id"), rs.getString("name"),
                rs.getString("normalized_name"), rs.getString("description"),
                rs.getString("status"), rs.getInt("asset_count"),
                rs.getObject("trash_started_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)), userId, externalId);
        return rows.stream().findFirst();
    }

    public Optional<KnowledgeBaseLifecycle> findForUpdate(long userId, String externalId) {
        List<KnowledgeBaseLifecycle> rows = jdbc.query("""
                SELECT id, external_id, name, normalized_name, description, status,
                       previous_status, trash_started_at, row_version
                  FROM knowledge_base
                 WHERE user_id = ? AND external_id = ? AND status <> 'PURGED'
                 FOR UPDATE
                """, (rs, rowNum) -> new KnowledgeBaseLifecycle(
                rs.getLong("id"), rs.getString("external_id"), rs.getString("name"),
                rs.getString("normalized_name"), rs.getString("description"),
                rs.getString("status"), rs.getString("previous_status"),
                rs.getObject("trash_started_at", LocalDateTime.class),
                rs.getLong("row_version")), userId, externalId);
        return rows.stream().findFirst();
    }

    public boolean activeNameExists(long userId, String normalizedName, long excludedId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM knowledge_base
                     WHERE user_id = ? AND normalized_name = ?
                       AND status IN ('ACTIVE', 'ARCHIVED') AND id <> ?
                )
                """, Boolean.class, userId, normalizedName, excludedId);
        return Boolean.TRUE.equals(exists);
    }

    public void update(long id, String name, String normalizedName, String description) {
        jdbc.update("""
                UPDATE knowledge_base
                   SET name = ?, normalized_name = ?, description = ?,
                       row_version = row_version + 1
                 WHERE id = ? AND status <> 'PURGED'
                """, name, normalizedName, description, id);
    }

    public void moveToTrash(long id, String previousStatus, LocalDateTime now) {
        jdbc.update("""
                UPDATE knowledge_base
                   SET status = 'TRASHED', previous_status = ?, trash_started_at = ?,
                       deleted_at = ?, row_version = row_version + 1
                 WHERE id = ? AND status IN ('ACTIVE', 'ARCHIVED')
                """, previousStatus, now, now, id);
    }

    public void restore(long id) {
        jdbc.update("""
                UPDATE knowledge_base
                   SET status = 'ACTIVE', previous_status = NULL, trash_started_at = NULL,
                       deleted_at = NULL, row_version = row_version + 1
                 WHERE id = ? AND status = 'TRASHED'
                """, id);
    }

    public void purge(long id) {
        jdbc.update("DELETE FROM knowledge_base_asset WHERE knowledge_base_id = ?", id);
        jdbc.update("""
                UPDATE knowledge_base
                   SET status = 'PURGED', row_version = row_version + 1
                 WHERE id = ? AND status = 'TRASHED'
                """, id);
    }

    public Optional<OwnedAsset> findActiveAssetForUpdate(long userId, String assetExternalId) {
        List<OwnedAsset> rows = jdbc.query("""
                SELECT id, external_id
                  FROM asset
                 WHERE user_id = ? AND external_id = ? AND status = 'ACTIVE'
                 FOR UPDATE
                """, (rs, rowNum) -> new OwnedAsset(
                rs.getLong("id"), rs.getString("external_id")), userId, assetExternalId);
        return rows.stream().findFirst();
    }

    public void addAsset(long knowledgeBaseId, long assetId, long userId) {
        Integer nextSortOrder = jdbc.queryForObject("""
                SELECT LEAST(COALESCE(MAX(sort_order), 0) + 10, 1000000)
                  FROM knowledge_base_asset
                 WHERE knowledge_base_id = ?
                """, Integer.class, knowledgeBaseId);
        jdbc.update("""
                INSERT IGNORE INTO knowledge_base_asset (
                    knowledge_base_id, asset_id, added_by_user_id, sort_order
                ) VALUES (?, ?, ?, ?)
                """, knowledgeBaseId, assetId, userId, nextSortOrder == null ? 0 : nextSortOrder);
    }

    public void removeAsset(long knowledgeBaseId, long assetId, long userId) {
        jdbc.update("""
                DELETE FROM knowledge_base_asset
                 WHERE knowledge_base_id = ? AND asset_id = ? AND added_by_user_id = ?
                """, knowledgeBaseId, assetId, userId);
    }

    public record PageCursor(LocalDateTime updatedAt, long id) {
    }

    public record KnowledgeBaseRow(
            long id,
            String externalId,
            String name,
            String normalizedName,
            String description,
            String status,
            int assetCount,
            LocalDateTime trashStartedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record KnowledgeBaseLifecycle(
            long id,
            String externalId,
            String name,
            String normalizedName,
            String description,
            String status,
            String previousStatus,
            LocalDateTime trashStartedAt,
            long rowVersion) {
    }

    public record OwnedAsset(long id, String externalId) {
    }
}
