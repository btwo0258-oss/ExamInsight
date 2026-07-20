package com.example.llm.service.impl;

import com.example.llm.integration.xfyun.XfyunEmbeddingClient;
import com.example.llm.service.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final XfyunEmbeddingClient embeddingClient;

    public EmbeddingServiceImpl(XfyunEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public List<Double> getEmbedding(String text) {
        return getQueryEmbedding(text);
    }

    @Override
    public List<List<Double>> getEmbeddings(List<String> texts) {
        List<List<Double>> result = new ArrayList<>(texts.size());
        for (String text : texts) result.add(getQueryEmbedding(text));
        return result;
    }

    @Override
    public List<Double> getDocumentEmbedding(String text) {
        return embeddingClient.embedDocument(text);
    }

    @Override
    public List<Double> getQueryEmbedding(String text) {
        return embeddingClient.embedQuery(text);
    }
}
