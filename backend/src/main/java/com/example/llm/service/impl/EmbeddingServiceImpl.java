package com.example.llm.service.impl;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.example.llm.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.embedding.model}")
    private String model;

    @Override
    public List<Double> getEmbedding(String text) {
        return getEmbeddings(Arrays.asList(text)).get(0);
    }

    @Override
    public List<List<Double>> getEmbeddings(List<String> texts) {
        try {
            CompletableFuture<List<List<Double>>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    TextEmbeddingParam param = TextEmbeddingParam.builder()
                            .apiKey(apiKey)
                            .model(model)
                            .texts(texts)
                            .build();
                    TextEmbedding embedding = new TextEmbedding();
                    TextEmbeddingResult result = embedding.call(param);

                    List<List<Double>> res = new ArrayList<>();
                    result.getOutput().getEmbeddings().forEach(emb -> {
                        res.add(emb.getEmbedding());
                    });
                    return res;
                } catch (Exception e) {
                    throw new RuntimeException("DashScope Embedding Failed: " + e.getMessage(), e);
                }
            });
            return future.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("DashScope Embedding timeout after 30 seconds", e);
        } catch (Exception e) {
            throw new RuntimeException("DashScope Embedding Failed: " + e.getMessage(), e);
        }
    }
}
