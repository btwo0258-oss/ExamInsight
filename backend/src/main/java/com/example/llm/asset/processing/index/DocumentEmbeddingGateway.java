package com.example.llm.asset.processing.index;

import java.util.List;

public interface DocumentEmbeddingGateway {
    List<Float> embedDocument(String text);
}
