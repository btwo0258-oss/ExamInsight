package com.example.llm.integration.xfyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class XfyunImageClient {

    private final XfyunConfig config;
    private final XfyunAuthSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public XfyunImageClient(XfyunConfig config, XfyunAuthSigner signer, ObjectMapper objectMapper) {
        this.config = config;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    public byte[] generate(String prompt, int width, int height) {
        config.requireApiCredentials();
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("图片描述不能为空");
        if (prompt.length() > 1000) throw new IllegalArgumentException("图片描述不能超过1000字");
        try {
            Map<String, Object> body = Map.of(
                    "header", Map.of("app_id", config.getAppId()),
                    "parameter", Map.of("chat", Map.of("domain", "general", "width", width, "height", height)),
                    "payload", Map.of("message", Map.of("text", List.of(Map.of("role", "user", "content", prompt))))
            );
            HttpRequest request = HttpRequest.newBuilder(signer.signedUrl(
                            config.getImageGenerationUrl(), "POST", config.getApiKey(), config.getApiSecret()))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("图片生成请求失败(" + response.statusCode() + "): " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("header").path("code").asInt(-1);
            if (code != 0) throw new IllegalStateException(root.path("header").path("message").asText("图片生成失败"));
            String content = root.path("payload").path("choices").path("text").path(0).path("content").asText();
            if (content.isBlank()) throw new IllegalStateException("图片生成服务未返回图片");
            return Base64.getDecoder().decode(content);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片生成请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("图片生成调用失败: " + e.getMessage(), e);
        }
    }
}
