package com.example.llm.service;

import java.util.List;

public interface EmbeddingService {
    List<Double> getEmbedding(String text);
    List<List<Double>> getEmbeddings(List<String> texts);
}
