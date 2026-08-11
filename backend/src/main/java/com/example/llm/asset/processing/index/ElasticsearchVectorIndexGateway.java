package com.example.llm.asset.processing.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ElasticsearchVectorIndexGateway implements VectorIndexGateway {
    private final ElasticsearchClient client;
    private final AssetProcessingProperties properties;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    public ElasticsearchVectorIndexGateway(
            ElasticsearchClient client,
            AssetProcessingProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void ensureIndex() {
        if (indexReady.get()) {
            return;
        }
        String indexName = properties.getIndexing().getIndexName();
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(request -> request.index(indexName))).value();
            if (exists) {
                indexReady.set(true);
                return;
            }
            String mapping = mappingJson(properties.getIndexing().getDimensions());
            try {
                client.indices().create(new CreateIndexRequest.Builder()
                        .index(indexName)
                        .withJson(new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8)))
                        .build());
            } catch (Exception raceOrFailure) {
                boolean createdByAnotherWorker = client.indices()
                        .exists(ExistsRequest.of(request -> request.index(indexName)))
                        .value();
                if (!createdByAnotherWorker) {
                    throw raceOrFailure;
                }
            }
            indexReady.set(true);
        } catch (Exception exception) {
            throw ProcessingFailure.retryable(
                    "VECTOR_INDEX_UNAVAILABLE",
                    "语义索引暂时不可用，后台稍后会自动重试。",
                    exception);
        }
    }

    @Override
    public void upsert(VectorDocument document) {
        try {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("chunk_external_id", document.chunkExternalId());
            source.put("owner_user_id", document.ownerUserId());
            source.put("asset_id", document.assetId());
            source.put("asset_version_id", document.assetVersionId());
            source.put("parse_result_id", document.parseResultId());
            source.put("sequence_no", document.sequenceNo());
            source.put("content_sha256", document.contentSha256());
            source.put("embedding_version", document.embeddingVersion());
            source.put("embedding", document.embedding());
            client.index(IndexRequest.of(request -> request
                    .index(properties.getIndexing().getIndexName())
                    .id(document.documentId())
                    .document(source)));
        } catch (Exception exception) {
            indexReady.set(false);
            throw ProcessingFailure.retryable(
                    "VECTOR_INDEX_WRITE_FAILED",
                    "语义索引写入失败，后台稍后会自动重试。",
                    exception);
        }
    }

    @Override
    public void deleteByAsset(long ownerUserId, long assetId) {
        String indexName = properties.getIndexing().getIndexName();
        try {
            boolean exists = client.indices().exists(
                    ExistsRequest.of(request -> request.index(indexName))).value();
            if (!exists) {
                return;
            }
            Query ownerFilter = Query.of(query -> query.term(term -> term
                    .field("owner_user_id")
                    .value(ownerUserId)));
            Query assetFilter = Query.of(query -> query.term(term -> term
                    .field("asset_id")
                    .value(assetId)));
            client.deleteByQuery(request -> request
                    .index(indexName)
                    .conflicts(Conflicts.Proceed)
                    .query(query -> query.bool(bool -> bool
                            .filter(ownerFilter, assetFilter))));
        } catch (Exception exception) {
            indexReady.set(false);
            throw ProcessingFailure.retryable(
                    "VECTOR_INDEX_DELETE_FAILED",
                    "资料索引暂时无法清理，后台稍后会自动重试。",
                    exception);
        }
    }

    private String mappingJson(int dimensions) {
        return """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0
                  },
                  "mappings": {
                    "dynamic": "strict",
                    "_source": { "excludes": ["embedding"] },
                    "properties": {
                      "chunk_external_id": { "type": "keyword" },
                      "owner_user_id": { "type": "long" },
                      "asset_id": { "type": "long" },
                      "asset_version_id": { "type": "long" },
                      "parse_result_id": { "type": "long" },
                      "sequence_no": { "type": "integer" },
                      "content_sha256": { "type": "keyword" },
                      "embedding_version": { "type": "keyword" },
                      "embedding": {
                        "type": "dense_vector",
                        "dims": %d,
                        "index": true,
                        "similarity": "cosine"
                      }
                    }
                  }
                }
                """.formatted(dimensions);
    }
}
