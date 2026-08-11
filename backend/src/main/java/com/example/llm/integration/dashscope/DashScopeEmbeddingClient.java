package com.example.llm.integration.dashscope;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DashScopeEmbeddingClient {
    private final ObjectMapper objectMapper;
    private final AssetProcessingProperties properties;
    private final DashScopeProperties dashScopeProperties;
    private final HttpClient httpClient;

    public DashScopeEmbeddingClient(
            ObjectMapper objectMapper,
            AssetProcessingProperties properties,
            DashScopeProperties dashScopeProperties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.dashScopeProperties = dashScopeProperties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public boolean isConfigured() {
        return dashScopeProperties.isConfigured();
    }

    public List<Double> embed(String text) {
        if (!isConfigured()) {
            throw new EmbeddingProviderException(
                    "EMBEDDING_NOT_CONFIGURED", "阿里云向量服务尚未配置。", false, null);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("向量化文本不能为空");
        }
        try {
            var indexing = properties.getIndexing();
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", indexing.getProviderModel(),
                    "input", text,
                    "dimensions", indexing.getDimensions(),
                    "encoding_format", "float"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            dashScopeProperties.openaiEndpoint("embeddings")))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + dashScopeProperties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw classify(response.statusCode(), response.body());
            }
            JsonNode embedding = objectMapper.readTree(response.body())
                    .path("data").path(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new EmbeddingProviderException(
                        "EMBEDDING_INVALID_RESPONSE", "阿里云向量服务返回了无效结果。", false, null);
            }
            List<Double> vector = new ArrayList<>(embedding.size());
            for (JsonNode value : embedding) vector.add(value.asDouble());
            return List.copyOf(vector);
        } catch (EmbeddingProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingProviderException(
                    "EMBEDDING_REQUEST_INTERRUPTED", "向量请求被中断。", true, exception);
        } catch (Exception exception) {
            throw new EmbeddingProviderException(
                    "EMBEDDING_PROVIDER_UNAVAILABLE", "阿里云向量服务暂时不可用。", true, exception);
        }
    }

    private EmbeddingProviderException classify(int status, String responseBody) {
        String providerCode = providerErrorCode(responseBody);
        log.warn("DashScope embedding request rejected, status={}, providerCode={}",
                status, providerCode.isBlank() ? "unknown" : providerCode);
        if (status == 401 || status == 403) {
            return new EmbeddingProviderException(
                    "EMBEDDING_AUTH_FAILED", "阿里云向量服务鉴权失败。", false, null);
        }
        if (status == 402 || providerCode.toLowerCase().contains("quota")) {
            return new EmbeddingProviderException(
                    "EMBEDDING_QUOTA_EXHAUSTED", "阿里云向量服务额度不足。", false, null);
        }
        if (status == 429) {
            return new EmbeddingProviderException(
                    "EMBEDDING_RATE_LIMITED", "阿里云向量服务请求过于频繁。", true, null);
        }
        boolean retryable = status >= 500;
        return new EmbeddingProviderException(
                retryable ? "EMBEDDING_PROVIDER_UNAVAILABLE" : "EMBEDDING_REQUEST_REJECTED",
                retryable ? "阿里云向量服务暂时不可用。" : "阿里云向量请求被拒绝。",
                retryable,
                null);
    }

    private String providerErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            String nestedCode = body.path("error").path("code").asText("");
            return nestedCode.isBlank() ? body.path("code").asText("") : nestedCode;
        } catch (Exception ignored) {
            return "";
        }
    }

    public static final class EmbeddingProviderException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public EmbeddingProviderException(String code, String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() { return code; }
        public boolean retryable() { return retryable; }
    }
}
