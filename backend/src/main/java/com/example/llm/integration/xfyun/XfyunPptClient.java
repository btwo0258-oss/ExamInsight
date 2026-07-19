package com.example.llm.integration.xfyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class XfyunPptClient {

    private final XfyunConfig config;
    private final XfyunAuthSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public XfyunPptClient(XfyunConfig config, XfyunAuthSigner signer, ObjectMapper objectMapper) {
        this.config = config;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    public JsonNode listTemplates() {
        return postJson("/template/list", Map.of("pageNum", 1, "pageSize", 100)).path("data");
    }

    public JsonNode createOutline(String query, String language) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("PPT主题不能为空");
        if (query.length() > 12000) throw new IllegalArgumentException("PPT生成要求不能超过12000字");
        return postMultipart("/createOutline", Map.of(
                "query", query, "language", providerLanguage(language), "search", "false"
        )).path("data");
    }

    public JsonNode createPresentation(String query, JsonNode outline, String templateId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("outline", outline);
        if (templateId != null && !templateId.isBlank()) body.put("templateId", templateId);
        body.put("language", "cn");
        body.put("isFigure", false);
        body.put("isCardNote", false);
        body.put("search", false);
        return postJson("/createPptByOutline", body).path("data");
    }

    public JsonNode progress(String sid) {
        config.requirePptCredentials();
        try {
            HttpRequest request = authenticated(HttpRequest.newBuilder(
                            URI.create(config.getPptBaseUrl() + "/progress?sid=" + java.net.URLEncoder.encode(
                                    sid, StandardCharsets.UTF_8))))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(response).path("data");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PPT进度查询被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("PPT进度查询失败: " + e.getMessage(), e);
        }
    }

    public byte[] download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("PPT文件下载失败(" + response.statusCode() + ")");
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PPT文件下载被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("PPT文件下载失败: " + e.getMessage(), e);
        }
    }

    private JsonNode postJson(String path, Object body) {
        config.requirePptCredentials();
        try {
            HttpRequest request = authenticated(HttpRequest.newBuilder(URI.create(config.getPptBaseUrl() + path)))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return parseResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PPT服务请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("PPT服务调用失败: " + e.getMessage(), e);
        }
    }

    private JsonNode postMultipart(String path, Map<String, String> fields) {
        config.requirePptCredentials();
        try {
            String boundary = "----ExamInsight" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (Map.Entry<String, String> field : fields.entrySet()) {
                output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.write(field.getValue().getBytes(StandardCharsets.UTF_8));
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = authenticated(HttpRequest.newBuilder(URI.create(config.getPptBaseUrl() + path)))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()))
                    .build();
            return parseResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PPT服务请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("PPT服务调用失败: " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder authenticated(HttpRequest.Builder builder) {
        long timestamp = System.currentTimeMillis() / 1000;
        return builder.header("appId", config.getAppId())
                .header("timestamp", String.valueOf(timestamp))
                .header("signature", signer.pptSignature(config.getAppId(), config.getApiSecret(), timestamp));
    }

    private JsonNode parseResponse(HttpResponse<String> response) throws Exception {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("PPT服务请求失败(" + response.statusCode() + "): " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("code").asInt(-1) != 0 || !root.path("flag").asBoolean(false)) {
            throw new IllegalStateException(root.path("desc").asText("PPT服务调用失败"));
        }
        return root;
    }

    private String providerLanguage(String language) {
        if (language == null) return "cn";
        return language.toLowerCase().startsWith("zh") ? "cn" : language.toLowerCase();
    }
}
