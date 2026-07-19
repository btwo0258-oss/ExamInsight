package com.example.llm.integration.xfyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class XfyunOcrClient {

    private final XfyunConfig config;
    private final XfyunAuthSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public XfyunOcrClient(XfyunConfig config, XfyunAuthSigner signer, ObjectMapper objectMapper) {
        this.config = config;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    public String recognize(byte[] image, String encoding) {
        config.requireApiCredentials();
        try {
            Map<String, Object> body = Map.of(
                    "header", Map.of("app_id", config.getAppId(), "status", 0),
                    "parameter", Map.of("ocr", Map.of(
                            "result_option", "normal,no_line_position",
                            "result_format", "json,markdown",
                            "output_type", "one_shot",
                            "exif_option", "0",
                            "markdown_element_option", "watermark=0,page_header=0,page_footer=0,page_number=0,graph=0",
                            "result", Map.of("encoding", "utf8", "compress", "raw", "format", "plain")
                    )),
                    "payload", Map.of("image", Map.of(
                            "encoding", encoding, "image", Base64.getEncoder().encodeToString(image), "status", 0, "seq", 0))
            );
            HttpRequest request = HttpRequest.newBuilder(signer.signedUrl(
                            config.getOcrUrl(), "POST", config.getApiKey(), config.getApiSecret()))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OCR 请求失败(" + response.statusCode() + "): " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("header").path("code").asInt(0);
            if (code != 0) throw new IllegalStateException(root.path("header").path("message").asText("OCR识别失败"));
            JsonNode result = root.path("payload").path("result");
            if (result.isMissingNode()) result = root.path("payload").path("ocr").path("result");
            String encoded = result.path("text").asText();
            if (encoded.isBlank()) throw new IllegalStateException("OCR服务未返回识别内容");
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            return readableText(decoded);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("OCR调用失败: " + e.getMessage(), e);
        }
    }

    private String readableText(String decoded) {
        try {
            JsonNode root = objectMapper.readTree(decoded);
            Set<String> values = new LinkedHashSet<>();
            collectText(root, values);
            if (!values.isEmpty()) return String.join("\n", values);
        } catch (Exception ignored) {
        }
        return decoded.trim();
    }

    private void collectText(JsonNode node, Set<String> values) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (("text".equals(entry.getKey()) || "content".equals(entry.getKey())
                        || "markdown".equals(entry.getKey())) && entry.getValue().isTextual()) {
                    String value = entry.getValue().asText().trim();
                    if (!value.isEmpty()) values.add(value);
                } else {
                    collectText(entry.getValue(), values);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectText(child, values));
        }
    }
}
