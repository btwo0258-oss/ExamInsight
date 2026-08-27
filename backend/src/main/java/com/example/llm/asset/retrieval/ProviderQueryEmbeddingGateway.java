package com.example.llm.asset.retrieval;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.asset.processing.index.EmbeddingRuntime;
import com.example.llm.integration.dashscope.DashScopeEmbeddingClient;
import com.example.llm.integration.dashscope.DashScopeEmbeddingClient.EmbeddingProviderException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProviderQueryEmbeddingGateway implements QueryEmbeddingGateway {
    private final DashScopeEmbeddingClient dashScope;
    private final EmbeddingRuntime runtime;
    private final AssetProcessingProperties properties;

    public ProviderQueryEmbeddingGateway(
            DashScopeEmbeddingClient dashScope,
            EmbeddingRuntime runtime,
            AssetProcessingProperties properties) {
        this.dashScope = dashScope;
        this.runtime = runtime;
        this.properties = properties;
    }

    @Override
    public List<Float> embedQuery(String query) {
        if (!runtime.isSemanticIndexAvailable()) {
            throw new RetrievalException(
                    "SEMANTIC_INDEX_NOT_CONFIGURED", "Semantic index provider is not configured");
        }
        try {
            if (!"dashscope".equals(runtime.provider())) {
                throw new RetrievalException(
                        "SEMANTIC_INDEX_PROVIDER_UNSUPPORTED",
                        "V2 semantic retrieval only supports the frozen DashScope embedding model");
            }
            List<Double> raw = dashScope.embed(query);
            List<Float> vector = validate(raw, properties.getIndexing().getDimensions());
            runtime.resume();
            return vector;
        } catch (RetrievalException exception) {
            throw exception;
        } catch (EmbeddingProviderException exception) {
            if (!exception.retryable()) runtime.suspend(exception.code());
            throw new RetrievalException(
                    exception.code(), "Query embedding provider is unavailable", exception);
        } catch (RuntimeException exception) {
            throw new RetrievalException(
                    "QUERY_EMBEDDING_UNAVAILABLE", "Query embedding provider is unavailable", exception);
        }
    }

    private List<Float> validate(List<Double> raw, int expectedDimensions) {
        if (raw.size() != expectedDimensions) {
            runtime.suspend("QUERY_EMBEDDING_DIMENSION_MISMATCH");
            throw new RetrievalException(
                    "QUERY_EMBEDDING_DIMENSION_MISMATCH", "Query embedding dimensions do not match the active index");
        }
        List<Float> vector = new ArrayList<>(raw.size());
        double magnitudeSquared = 0;
        for (Double value : raw) {
            if (value == null || !Double.isFinite(value)) {
                runtime.suspend("QUERY_EMBEDDING_INVALID");
                throw new RetrievalException("QUERY_EMBEDDING_INVALID", "Query embedding contains an invalid value");
            }
            float converted = value.floatValue();
            if (!Float.isFinite(converted)) {
                runtime.suspend("QUERY_EMBEDDING_INVALID");
                throw new RetrievalException("QUERY_EMBEDDING_INVALID", "Query embedding contains an invalid value");
            }
            vector.add(converted);
            magnitudeSquared += (double) converted * converted;
        }
        if (!(magnitudeSquared > 0)) {
            runtime.suspend("QUERY_EMBEDDING_ZERO_VECTOR");
            throw new RetrievalException("QUERY_EMBEDDING_ZERO_VECTOR", "Query embedding is a zero vector");
        }
        return List.copyOf(vector);
    }
}
