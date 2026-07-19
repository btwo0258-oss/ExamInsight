package com.example.llm.integration.xfyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Component
public class XfyunVisionClient {

    private final XfyunConfig config;
    private final XfyunAuthSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public XfyunVisionClient(XfyunConfig config, XfyunAuthSigner signer, ObjectMapper objectMapper) {
        this.config = config;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    public String understand(byte[] image, String prompt) {
        config.requireApiCredentials();
        try {
            Map<String, Object> body = Map.of(
                    "header", Map.of("app_id", config.getAppId()),
                    "parameter", Map.of("chat", Map.of(
                            "domain", "imagev3", "temperature", 0.3, "top_k", 4, "max_tokens", 4096)),
                    "payload", Map.of("message", Map.of("text", List.of(
                            Map.of("role", "user", "content", Base64.getEncoder().encodeToString(image), "content_type", "image"),
                            Map.of("role", "user", "content", prompt == null || prompt.isBlank() ? "请描述并理解这张图片" : prompt, "content_type", "text")
                    )))
            );
            return exchange(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("图片理解调用失败: " + e.getMessage(), e);
        }
    }

    private String exchange(String requestJson) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        StringBuilder answer = new StringBuilder();
        StringBuilder frame = new StringBuilder();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                webSocket.sendText(requestJson, true);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                frame.append(data);
                if (last) {
                    try {
                        JsonNode root = objectMapper.readTree(frame.toString());
                        frame.setLength(0);
                        int code = root.path("header").path("code").asInt(0);
                        if (code != 0) {
                            result.completeExceptionally(new IllegalStateException(
                                    root.path("header").path("message").asText("图片理解失败")));
                        } else {
                            JsonNode texts = root.path("payload").path("choices").path("text");
                            if (texts.isArray()) for (JsonNode item : texts) answer.append(item.path("content").asText(""));
                            if (root.path("header").path("status").asInt() == 2
                                    || root.path("payload").path("choices").path("status").asInt() == 2) {
                                result.complete(answer.toString());
                            }
                        }
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                result.completeExceptionally(error);
            }
        };
        WebSocket socket = httpClient.newWebSocketBuilder().buildAsync(
                signer.signedUrl(config.getImageUnderstandingUrl(), "GET", config.getApiKey(), config.getApiSecret()),
                listener).join();
        try {
            String value = result.get(120, TimeUnit.SECONDS);
            if (value == null || value.isBlank()) throw new IllegalStateException("图片理解服务未返回有效内容");
            return value;
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }
}
