package com.example.llm.asset.processing.repository;

import com.example.llm.asset.processing.ProcessingFailure;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class AssetPurgeRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public AssetPurgeRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public PurgeTarget prepare(String assetExternalId) {
        return Objects.requireNonNull(transactions.execute(status -> {
            AssetState asset = lockAsset(assetExternalId);
            if (asset.status().equals("PURGED")) {
                return new PurgeTarget(
                        asset.id(), asset.externalId(), asset.userId(), true, List.of());
            }
            if (!asset.status().equals("TRASHED")) {
                throw ProcessingFailure.terminal(
                        "ASSET_NOT_TRASHED",
                        "只有回收站中的资料才能永久删除。");
            }
            assertPurgeable(asset.id());
            List<StorageTarget> storageObjects = jdbc.query("""
                    SELECT DISTINCT so.id, so.external_id, so.object_key_ciphertext
                      FROM asset_version av
                      JOIN storage_object so ON so.id = av.storage_object_id
                     WHERE av.asset_id = ? AND so.status <> 'PURGED'
                     ORDER BY so.id ASC
                    """, (rs, rowNum) -> new StorageTarget(
                    rs.getLong("id"),
                    rs.getString("external_id"),
                    rs.getBytes("object_key_ciphertext")), asset.id());
            return new PurgeTarget(
                    asset.id(), asset.externalId(), asset.userId(), false, storageObjects);
        }));
    }

    public void complete(PurgeTarget target, LocalDateTime purgedAt) {
        transactions.executeWithoutResult(status -> {
            AssetState asset = lockAsset(target.assetExternalId());
            if (asset.status().equals("PURGED")) {
                return;
            }
            if (!asset.status().equals("TRASHED")) {
                throw ProcessingFailure.terminal(
                        "ASSET_NOT_TRASHED",
                        "资料已不在回收站中，停止永久删除。");
            }
            assertPurgeable(asset.id());
            jdbc.update("DELETE FROM knowledge_base_asset WHERE asset_id = ?", asset.id());
            jdbc.update("""
                    UPDATE asset
                       SET current_version_id = NULL, row_version = row_version + 1
                     WHERE id = ?
                    """, asset.id());
            jdbc.update("DELETE FROM asset_version WHERE asset_id = ?", asset.id());
            if (!target.storageObjects().isEmpty()) {
                String placeholders = String.join(
                        ",", java.util.Collections.nCopies(target.storageObjects().size(), "?"));
                List<Object> args = new ArrayList<>();
                args.add(purgedAt);
                target.storageObjects().forEach(storage -> args.add(storage.id()));
                jdbc.update("""
                        UPDATE storage_object
                           SET status = 'PURGED', purged_at = ?, row_version = row_version + 1
                         WHERE id IN (%s) AND status <> 'PURGED'
                        """.formatted(placeholders), args.toArray());
            }
            jdbc.update("""
                    UPDATE asset
                       SET status = 'PURGED', row_version = row_version + 1
                     WHERE id = ? AND status = 'TRASHED'
                    """, asset.id());
        });
    }

    private AssetState lockAsset(String externalId) {
        List<AssetState> rows = jdbc.query("""
                SELECT id, external_id, user_id, status
                  FROM asset
                 WHERE external_id = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new AssetState(
                rs.getLong("id"), rs.getString("external_id"),
                rs.getLong("user_id"), rs.getString("status")), externalId);
        if (rows.isEmpty()) {
            throw ProcessingFailure.terminal("ASSET_NOT_FOUND", "待删除资料不存在。");
        }
        return rows.get(0);
    }

    private void assertPurgeable(long assetId) {
        if (hasActiveProcessing(assetId)) {
            throw ProcessingFailure.retryable(
                    "ASSET_PROCESSING_IN_PROGRESS",
                    "资料仍在后台处理中，完成后会继续删除。",
                    null);
        }
        if (hasDurableReferences(assetId)) {
            throw ProcessingFailure.terminal(
                    "ASSET_VERSION_IN_USE",
                    "资料仍被对话、引用或其他已确认内容使用，不能永久删除。");
        }
    }

    private boolean hasActiveProcessing(long assetId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                      FROM async_job job
                     WHERE job.status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'CANCELLING')
                       AND (
                           (
                               job.job_type = 'FILE_SECURITY_SCAN'
                               AND EXISTS (
                                   SELECT 1
                                     FROM asset_version av
                                     JOIN storage_object so ON so.id = av.storage_object_id
                                    WHERE av.asset_id = ?
                                      AND so.external_id = job.aggregate_external_id
                               )
                           )
                           OR (
                               job.job_type = 'FILE_PARSE'
                               AND EXISTS (
                                   SELECT 1 FROM asset_version av
                                    WHERE av.asset_id = ?
                                      AND av.external_id = job.aggregate_external_id
                               )
                           )
                           OR (
                               job.job_type = 'FILE_INDEX'
                               AND EXISTS (
                                   SELECT 1
                                     FROM document_chunk c
                                     JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                                     JOIN asset_version av ON av.id = pr.asset_version_id
                                    WHERE av.asset_id = ?
                                      AND c.external_id = job.aggregate_external_id
                               )
                           )
                       )
                )
                """, Boolean.class, assetId, assetId, assetId);
        return Boolean.TRUE.equals(exists);
    }

    private boolean hasDurableReferences(long assetId) {
        Long count = jdbc.queryForObject("""
                SELECT
                    (SELECT COUNT(*)
                       FROM message_attachment attachment
                       JOIN asset_version av ON av.id = attachment.asset_version_id
                      WHERE av.asset_id = ?)
                  + (SELECT COUNT(*)
                       FROM message_citation citation
                       JOIN document_chunk c ON c.id = citation.document_chunk_id
                       JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                       JOIN asset_version av ON av.id = pr.asset_version_id
                      WHERE av.asset_id = ?)
                  + (SELECT COUNT(*)
                       FROM retrieval_result result
                       JOIN document_chunk c ON c.id = result.document_chunk_id
                       JOIN asset_parse_result pr ON pr.id = c.parse_result_id
                       JOIN asset_version av ON av.id = pr.asset_version_id
                      WHERE av.asset_id = ?)
                """, Long.class, assetId, assetId, assetId);
        return count != null && count > 0;
    }

    private record AssetState(long id, String externalId, long userId, String status) {
    }

    public record StorageTarget(long id, String externalId, byte[] objectKeyCiphertext) {
        public StorageTarget {
            objectKeyCiphertext = objectKeyCiphertext == null
                    ? null
                    : objectKeyCiphertext.clone();
        }

        @Override
        public byte[] objectKeyCiphertext() {
            return objectKeyCiphertext == null ? null : objectKeyCiphertext.clone();
        }
    }

    public record PurgeTarget(
            long assetId,
            String assetExternalId,
            long ownerUserId,
            boolean alreadyPurged,
            List<StorageTarget> storageObjects) {
        public PurgeTarget {
            storageObjects = storageObjects == null ? List.of() : List.copyOf(storageObjects);
        }
    }
}
