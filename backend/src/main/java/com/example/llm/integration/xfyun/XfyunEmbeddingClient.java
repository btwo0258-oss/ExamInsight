package com.example.llm.integration.xfyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class XfyunEmbeddingClient {

    private static final long MIN_REQUEST_INTERVAL_MILLIS = 1300L;
    private static final int MAX_RATE_LIMIT_ATTEMPTS = 4;

    private final XfyunConfig config;
    private final XfyunAuthSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final Object rateLimitLock = new Object();
    private long nextRequestAtMillis;

    public XfyunEmbeddingClient(XfyunConfig config, XfyunAuthSigner signer, ObjectMapper objectMapper) {
        this.config = config;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    public List<Double> embedDocument(String text) {
        return embed(text, config.getEmbeddingDocumentUrl(), "para", "文档");
    }

    public List<Double> embedQuery(String text) {
        return embed(text, config.getEmbeddingQueryUrl(), "query", "问题");
    }

    private List<Double> embed(String text, String endpoint, String domain, String type) {
        config.requireApiCredentials();
        if (text == null || text.isBlank()) throw new IllegalArgumentException("向量化文本不能为空");
        try {
            String messageJson = objectMapper.writeValueAsString(Map.of(
                    "messages", List.of(Map.of("content", text, "role", "user"))));
            String encodedText = Base64.getEncoder().encodeToString(
                    messageJson.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> body = Map.of(
                    "header", Map.of("app_id", config.getAppId(), "uid", "examinsight", "status", 3),
                    "parameter", Map.of("emb", Map.of(
                            "domain", domain,
                            "feature", Map.of("encoding", "utf8", "compress", "raw", "format", "plain"))),
                    "payload", Map.of("messages", Map.of(
                            "encoding", "utf8", "compress", "raw", "format", "json",
                            "status", 3, "text", encodedText)));

            HttpRequest request = HttpRequest.newBuilder(signer.signedUrl(
                            endpoint, "POST", config.getApiKey(), config.getApiSecret()))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= MAX_RATE_LIMIT_ATTEMPTS; attempt++) {
                awaitRequestSlot();
                response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (!isRateLimitResponse(response) || attempt == MAX_RATE_LIMIT_ATTEMPTS) break;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String responseBody = response.body() == null ? "" : response.body().trim();
                if (responseBody.length() > 500) responseBody = responseBody.substring(0, 500);
                throw new IllegalStateException("讯飞" + type + "向量请求失败(" + response.statusCode()
                        + "): " + responseBody);
            }
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("header").path("code").asInt(-1);
            if (code != 0) {
                String message = root.path("header").path("message").asText("未知错误");
                throw new IllegalStateException("讯飞" + type + "向量失败[" + code + "]: " + message);
            }
            String encodedVector = root.path("payload").path("feature").path("text").asText();
            if (encodedVector.isBlank()) throw new IllegalStateException("讯飞向量服务未返回向量数据");
            List<Double> vector = decodeVector(encodedVector);
            if (vector.size() > config.getEmbeddingDimensions()) {
                vector = new ArrayList<>(vector.subList(0, config.getEmbeddingDimensions()));
            }
            if (vector.size() != config.getEmbeddingDimensions()) {
                throw new IllegalStateException("讯飞向量维度异常，期望 "
                        + config.getEmbeddingDimensions() + "，实际 " + vector.size());
            }
            return vector;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("讯飞向量请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("讯飞" + type + "向量调用失败: " + e.getMessage(), e);
        }
    }

    private void awaitRequestSlot() throws InterruptedException {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long waitMillis = nextRequestAtMillis - now;
            if (waitMillis > 0) Thread.sleep(waitMillis);
            nextRequestAtMillis = System.currentTimeMillis() + MIN_REQUEST_INTERVAL_MILLIS;
        }
    }

    private boolean isRateLimitResponse(HttpResponse<String> response) {
        String body = response.body();
        return body != null && (body.contains("\"code\":11202") || body.contains("\"code\":11203"));
    }

    private List<Double> decodeVector(String encodedVector) {
        byte[] bytes = Base64.getDecoder().decode(encodedVector);
        if (bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            throw new IllegalStateException("讯飞向量数据格式错误");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        List<Double> vector = new ArrayList<>(bytes.length / Float.BYTES);
        while (buffer.remaining() >= Float.BYTES) vector.add((double) buffer.getFloat());
        return vector;
    }
}
