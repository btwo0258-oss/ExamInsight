package com.example.llm.asset.retrieval;

import java.util.List;

public interface QueryEmbeddingGateway {
    List<Float> embedQuery(String query);
}
