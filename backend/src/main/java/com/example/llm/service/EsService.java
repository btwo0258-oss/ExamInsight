package com.example.llm.service;

import java.util.List;

public interface EsService {
    void createIndexIfNotExists(String indexName);
    void saveChunk(String indexName, String esId, Long kbId, Long docId, Integer chunkIndex, String content, List<Double> embedding);
    void deleteByDocId(String indexName, Long docId);
    void deleteByKbId(String indexName, Long kbId);
    
    // RAG kNN search
    List<java.util.Map<String, Object>> searchSimilarChunks(String indexName, Long kbId, List<Double> vector, int topK, double minScore);
}
