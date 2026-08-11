package com.example.llm.asset.processing.index;

import com.example.llm.asset.processing.ProcessingFailure;
import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.integration.dashscope.DashScopeEmbeddingClient;
import com.example.llm.integration.dashscope.DashScopeEmbeddingClient.EmbeddingProviderException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProviderDocumentEmbeddingGateway implements DocumentEmbeddingGateway {
    private final DashScopeEmbeddingClient dashScope;
    private final EmbeddingRuntime runtime;
    private final AssetProcessingProperties properties;

    public ProviderDocumentEmbeddingGateway(
            DashScopeEmbeddingClient dashScope,
            EmbeddingRuntime runtime,
            AssetProcessingProperties properties) {
        this.dashScope = dashScope;
        this.runtime = runtime;
        this.properties = properties;
    }

    @Override
    public List<Float> embedDocument(String text) {
        if (!runtime.isSemanticIndexAvailable()) {
            throw ProcessingFailure.terminal(
                    "EMBEDDING_NOT_CONFIGURED", "语义索引服务尚未配置，关键词检索仍可使用。");
        }
        try {
            if (!"dashscope".equals(runtime.provider())) {
                throw ProcessingFailure.terminal(
                        "EMBEDDING_PROVIDER_UNSUPPORTED", "V2 向量索引仅允许使用已冻结的阿里云向量模型。");
            }
            List<Double> raw = dashScope.embed(text);
            return validate(raw, properties.getIndexing().getDimensions());
        } catch (ProcessingFailure failure) {
            throw failure;
        } catch (EmbeddingProviderException exception) {
            if (!exception.retryable()) runtime.suspend(exception.code());
            throw exception.retryable()
                    ? ProcessingFailure.retryable(exception.code(), exception.getMessage(), exception)
                    : ProcessingFailure.terminal(exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            throw ProcessingFailure.retryable(
                    "EMBEDDING_PROVIDER_UNAVAILABLE", "向量服务暂时不可用，后台稍后会自动重试。", exception);
        }
    }

    private List<Float> validate(List<Double> raw, int expectedDimensions) {
        if (raw.size() != expectedDimensions) {
            runtime.suspend("EMBEDDING_DIMENSION_MISMATCH");
            throw ProcessingFailure.terminal(
                    "EMBEDDING_DIMENSION_MISMATCH", "向量模型返回了不兼容的维度，索引已停止。");
        }
        List<Float> vector = new ArrayList<>(raw.size());
        double magnitudeSquared = 0;
        for (Double value : raw) {
            if (value == null || !Double.isFinite(value)) {
                runtime.suspend("INVALID_EMBEDDING_VECTOR");
                throw ProcessingFailure.terminal(
                        "INVALID_EMBEDDING_VECTOR", "向量模型返回了无效数据，索引已停止。");
            }
            float converted = value.floatValue();
            if (!Float.isFinite(converted)) {
                runtime.suspend("INVALID_EMBEDDING_VECTOR");
                throw ProcessingFailure.terminal(
                        "INVALID_EMBEDDING_VECTOR", "向量模型返回了无效数据，索引已停止。");
            }
            vector.add(converted);
            magnitudeSquared += (double) converted * converted;
        }
        if (!(magnitudeSquared > 0)) {
            runtime.suspend("ZERO_EMBEDDING_VECTOR");
            throw ProcessingFailure.terminal(
                    "ZERO_EMBEDDING_VECTOR", "向量模型返回了零向量，无法执行相似度检索。");
        }
        return List.copyOf(vector);
    }
}
