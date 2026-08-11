package com.example.llm.asset.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ElasticsearchVectorSearchGateway implements VectorSearchGateway {
    private final ElasticsearchClient client;
    private final AssetProcessingProperties processingProperties;

    public ElasticsearchVectorSearchGateway(
            ElasticsearchClient client,
            AssetProcessingProperties processingProperties) {
        this.client = client;
        this.processingProperties = processingProperties;
    }

    @Override
    public List<VectorHit> search(
            List<Float> queryVector,
            long ownerUserId,
            List<Long> allowedAssetIds,
            List<Long> allowedVersionIds,
            int topK,
            int numCandidates,
            double minScore) {
        if ((allowedAssetIds != null && allowedAssetIds.isEmpty())
                || (allowedVersionIds != null && allowedVersionIds.isEmpty())) {
            return List.of();
        }
        try {
            List<Query> filters = new ArrayList<>();
            filters.add(Query.of(query -> query.term(term -> term
                    .field("owner_user_id")
                    .value(ownerUserId))));
            if (allowedAssetIds != null) {
                filters.add(termsFilter("asset_id", allowedAssetIds));
            }
            if (allowedVersionIds != null) {
                filters.add(termsFilter("asset_version_id", allowedVersionIds));
            }

            SearchRequest request = new SearchRequest.Builder()
                    .index(processingProperties.getIndexing().getIndexName())
                    .size(topK)
                    .knn(knn -> knn
                            .field("embedding")
                            .queryVector(queryVector)
                            .k(topK)
                            .numCandidates(numCandidates)
                            .filter(filters))
                    .source(source -> source.filter(filter -> filter
                            .includes("chunk_external_id")))
                    .build();
            SearchResponse<Map> response = client.search(request, Map.class);
            List<VectorHit> hits = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                Object chunkExternalId = source == null ? null : source.get("chunk_external_id");
                Double score = hit.score();
                if (chunkExternalId != null && score != null && score >= minScore) {
                    hits.add(new VectorHit(String.valueOf(chunkExternalId), clamp(score)));
                }
            }
            return List.copyOf(hits);
        } catch (Exception exception) {
            throw new RetrievalException(
                    "VECTOR_SEARCH_UNAVAILABLE",
                    "Vector search is unavailable",
                    exception);
        }
    }

    private Query termsFilter(String field, List<Long> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
        return Query.of(query -> query.terms(terms -> terms
                .field(field)
                .terms(termsValues -> termsValues.value(fieldValues))));
    }

    private double clamp(double score) {
        return Math.max(0, Math.min(1, score));
    }
}
