package com.example.llm.asset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LibraryDtos {
    private LibraryDtos() {
    }

    public record Page<T>(List<T> items, String nextCursor) {
        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record RenameAssetRequest(
            @NotBlank
            @Size(max = 255)
            String name) {
    }

    public record AssetVersionView(
            String versionId,
            int versionNumber,
            String status,
            String mimeType,
            long sizeBytes,
            int chunkCount,
            int indexedChunkCount,
            int failedChunkCount,
            String indexStatus,
            Instant createdAt) {
    }

    public record AssetItem(
            String assetId,
            String name,
            String assetType,
            String sourceType,
            String status,
            int knowledgeBaseCount,
            AssetVersionView version,
            Instant trashedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record AssetDetail(
            AssetItem asset,
            List<KnowledgeBaseReference> knowledgeBases,
            PurgeJobView purgeJob) {
        public AssetDetail {
            knowledgeBases = knowledgeBases == null ? List.of() : List.copyOf(knowledgeBases);
        }
    }

    public record KnowledgeBaseReference(String knowledgeBaseId, String name) {
    }

    public record PurgeJobView(
            String jobId,
            String status,
            String errorCode,
            Instant requestedAt,
            Instant finishedAt) {
    }

    public record CreateKnowledgeBaseRequest(
            @NotBlank
            @Size(max = 160)
            String name,

            @Size(max = 1000)
            String description) {
    }

    public record UpdateKnowledgeBaseRequest(
            @Size(max = 160)
            String name,

            @Size(max = 1000)
            String description) {
    }

    public record KnowledgeBaseItem(
            String knowledgeBaseId,
            String name,
            String description,
            String status,
            int assetCount,
            Instant trashedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record KnowledgeBaseDetail(
            KnowledgeBaseItem knowledgeBase) {
    }
}
