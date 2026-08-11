package com.example.llm.asset.retrieval;

import java.util.List;

public interface VectorSearchGateway {
    List<VectorHit> search(
            List<Float> queryVector,
            long ownerUserId,
            List<Long> allowedAssetIds,
            List<Long> allowedVersionIds,
            int topK,
            int numCandidates,
            double minScore);

    record VectorHit(String chunkExternalId, double score) {
    }
}
