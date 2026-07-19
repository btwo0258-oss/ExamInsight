package com.example.llm.integration.xfyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class XfyunSparkClient {

    private final XfyunConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public XfyunSparkClient(XfyunConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public String stream(List<Map<String, String>> messages, Long userId, Consumer<String> onDelta) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "spark-x");
            body.put("user", "user_" + userId);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("thinking", Map.of("type", "auto"));
            body.put("max_tokens", 8192);

            HttpRequest request = HttpRequest.newBuilder(URI.create(config.getSparkUrl()))
                    .timeout(Duration.ofMinutes(3))
                    .header("Authorization", "Bearer " + config.getSparkApiPassword())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("Spark X2 请求失败(" + response.statusCode() + "): " + error);
            }

            StringBuilder answer = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String data = line.trim();
                    if (data.isEmpty()) continue;
                    if (data.startsWith("data:")) data = data.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JsonNode root = objectMapper.readTree(data);
                    if (root.path("code").asInt(0) != 0) {
                        throw new IllegalStateException(root.path("message").asText("Spark X2 服务异常"));
                    }
                    JsonNode choices = root.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) continue;
                    String delta = choices.get(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        answer.append(delta);
                        onDelta.accept(delta);
                    }
                }
            }
            if (answer.isEmpty()) throw new IllegalStateException("Spark X2 未返回有效回答");
            return answer.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Spark X2 请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("Spark X2 调用失败: " + e.getMessage(), e);
        }
    }
}
