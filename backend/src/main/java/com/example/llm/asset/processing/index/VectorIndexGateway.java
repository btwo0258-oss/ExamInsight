package com.example.llm.asset.processing.index;

import java.util.List;

public interface VectorIndexGateway {
    void ensureIndex();

    void upsert(VectorDocument document);

    void deleteByAsset(long ownerUserId, long assetId);

    record VectorDocument(
            String documentId,
            String chunkExternalId,
            long ownerUserId,
            long assetId,
            long assetVersionId,
            long parseResultId,
            int sequenceNo,
            String contentSha256,
            String embeddingVersion,
            List<Float> embedding) {
    }
}
