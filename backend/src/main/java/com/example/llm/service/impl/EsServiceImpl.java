package com.example.llm.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.llm.service.EsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EsServiceImpl implements EsService {

    @Autowired
    private ElasticsearchClient esClient;

    @Override
    public void createIndexIfNotExists(String indexName) {
        try {
            boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (!exists) {
                String mappingJson = "{\n" +
                        "  \"mappings\": {\n" +
                        "    \"properties\": {\n" +
                        "      \"kbId\": { \"type\": \"keyword\" },\n" +
                        "      \"docId\": { \"type\": \"keyword\" },\n" +
                        "      \"chunkIndex\": { \"type\": \"integer\" },\n" +
                        "      \"content\": { \"type\": \"text\", \"analyzer\": \"standard\" },\n" +
                        "      \"embedding\": { \"type\": \"dense_vector\", \"dims\": 1536, \"index\": true, \"similarity\": \"cosine\" }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}";
                
                esClient.indices().create(new CreateIndexRequest.Builder()
                        .index(indexName)
                        .withJson(new ByteArrayInputStream(mappingJson.getBytes("UTF-8")))
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("创建 ES 索引失败", e);
        }
    }

    @Override
    public void saveChunk(String indexName, String esId, Long kbId, Long docId, Integer chunkIndex, String content, List<Double> embedding) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("kbId", kbId.toString());
            doc.put("docId", docId.toString());
            doc.put("chunkIndex", chunkIndex);
            doc.put("content", content);
            doc.put("embedding", embedding);

            esClient.index(IndexRequest.of(i -> i
                    .index(indexName)
                    .id(esId)
                    .document(doc)
            ));
        } catch (Exception e) {
            throw new RuntimeException("保存 ES 文档失败", e);
        }
    }

    @Override
    public void deleteByDocId(String indexName, Long docId) {
        try {
            esClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("docId").value(docId.toString())))
            ));
        } catch (Exception e) {
            // log error
        }
    }

    @Override
    public void deleteByKbId(String indexName, Long kbId) {
        try {
            esClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("kbId").value(kbId.toString())))
            ));
        } catch (Exception e) {
            // log error
        }
    }

    @Override
    public List<Map<String, Object>> searchSimilarChunks(String indexName, Long kbId, List<Double> vector, int topK, double minScore) {
        try {
            // Because co.elastic.clients uses knn search
            co.elastic.clients.elasticsearch.core.SearchRequest searchRequest = new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
                .index(indexName)
                .knn(k -> k
                    .field("embedding")
                    .queryVector(vector.stream().map(Double::floatValue).collect(java.util.stream.Collectors.toList()))
                    .k(topK)
                    .numCandidates(topK * 10)
                    .filter(f -> f.term(t -> t.field("kbId").value(kbId.toString())))
                )
                .source(s -> s.filter(f -> f.excludes("embedding"))) // exclude large vector from response
                .build();
            
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> response = esClient.search(searchRequest, Map.class);
            List<Map<String, Object>> results = new java.util.ArrayList<>();
            
            for (co.elastic.clients.elasticsearch.core.search.Hit<Map> hit : response.hits().hits()) {
                if (hit.score() != null && hit.score() >= minScore) {
                    Map<String, Object> result = new HashMap<>(hit.source());
                    result.put("_score", hit.score());
                    result.put("_id", hit.id());
                    results.add(result);
                }
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("ES kNN 检索失败", e);
        }
    }
}
