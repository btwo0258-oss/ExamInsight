package com.example.llm.integration.dashscope;

import com.example.llm.integration.ai.AiCallResult;
import com.example.llm.integration.ai.AiChatMessage;
import com.example.llm.integration.ai.ProviderCallException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class DashScopeAiClient {
    public static final String PROVIDER = "dashscope";

    private final DashScopeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DashScopeAiClient(DashScopeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration connectTimeout = properties.getConnectTimeout() == null
                ? Duration.ofSeconds(15) : properties.getConnectTimeout();
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public AiCallResult<String> streamChat(List<AiChatMessage> messages, Consumer<String> onDelta) {
        String model = properties.getChat().getModel();
        ensureConfigured(model);
        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(this::chatMessage).toList());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        body.put("enable_thinking", false);

        HttpRequest request = jsonRequest(properties.openaiEndpoint("chat/completions"), body, model);
        Map<String, Object> usage = new LinkedHashMap<>();
        StringBuilder answer = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw classify(model, response.statusCode(), errorBody, null);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
                    JsonNode event = objectMapper.readTree(payload);
                    String delta = event.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        answer.append(delta);
                        onDelta.accept(delta);
                    }
                    if (event.has("usage")) usage.putAll(toMap(event.path("usage")));
                }
            }
            if (answer.isEmpty()) {
                throw failure(model, "DASHSCOPE_EMPTY_RESPONSE",
                        ProviderCallException.Category.INVALID_RESPONSE, false,
                        "阿里云对话服务未返回有效内容", null);
            }
            return result(answer.toString(), model, startedAt, usage);
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(model, "DASHSCOPE_INTERRUPTED", ProviderCallException.Category.INTERRUPTED,
                    false, "阿里云对话请求被中断", exception);
        } catch (Exception exception) {
            throw transportFailure(model, exception);
        }
    }

    public AiCallResult<String> completeText(List<AiChatMessage> messages) {
        String model = properties.getChat().getModel();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(this::chatMessage).toList());
        body.put("stream", false);
        body.put("enable_thinking", false);
        return textCompletion(model, body);
    }

    public AiCallResult<String> completeLearningOutput(
            List<AiChatMessage> messages, String schemaName, Map<String, Object> schema) {
        String model = properties.getChat().getModel();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(this::chatMessage).toList());
        body.put("stream", false);
        body.put("enable_thinking", false);
        if (schema != null) {
            body.put("response_format", Map.of("type", "json_schema", "json_schema", Map.of(
                    "name", schemaName, "strict", true, "schema", schema)));
        }
        // Do not introduce a low max_tokens cap that can cut a JSON object in half.
        // Learning bounds output size through small batches and validates finish_reason.
        return textCompletion(model, body, true);
    }

    public AiCallResult<String> recognize(byte[] image, String mimeType) {
        String model = properties.getOcr().getModel();
        validateImage(image, mimeType, model);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUri(image, mimeType))),
                        Map.of("type", "text", "text", "请按原顺序提取全部文字、表格和公式，不要推测缺失内容。"))
        )));
        body.put("ocr_options", Map.of("task", "text_recognition"));
        return textCompletion(model, body);
    }

    public AiCallResult<String> understand(byte[] image, String mimeType, String prompt) {
        String model = properties.getVision().getModel();
        validateImage(image, mimeType, model);
        String instruction = prompt == null || prompt.isBlank() ? "请识别并说明这张图片的主要内容" : prompt;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUri(image, mimeType))),
                        Map.of("type", "text", "text", instruction))
        )));
        body.put("enable_thinking", false);
        return textCompletion(model, body);
    }

    public AiCallResult<String> transcribe(byte[] audio, String mimeType) {
        String model = properties.getSpeech().getModel();
        ensureConfigured(model);
        if (audio == null || audio.length == 0) {
            throw failure(model, "EMPTY_AUDIO", ProviderCallException.Category.BAD_REQUEST,
                    false, "音频不能为空", null);
        }
        if (audio.length > 10L * 1024 * 1024) {
            throw failure(model, "AUDIO_TOO_LARGE", ProviderCallException.Category.UNSUPPORTED_INPUT,
                    false, "语音文件不能超过10MB", null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_audio",
                        "input_audio", Map.of("data", dataUri(audio, mimeType))))
        )));
        body.put("stream", false);
        body.put("asr_options", Map.of("enable_itn", true));
        return textCompletion(model, body);
    }

    public AiCallResult<byte[]> synthesizeSpeech(String text) {
        DashScopeProperties.SpeechSynthesis configuration = properties.getSpeechSynthesis();
        String model = configuration.getModel();
        ensureConfigured(model);
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isBlank()) {
            throw failure(model, "EMPTY_TTS_TEXT", ProviderCallException.Category.BAD_REQUEST,
                    false, "朗读文本不能为空", null);
        }
        if (normalizedText.codePointCount(0, normalizedText.length()) > 600) {
            throw failure(model, "TTS_TEXT_TOO_LONG", ProviderCallException.Category.UNSUPPORTED_INPUT,
                    false, "单次朗读文本不能超过 600 个字符", null);
        }

        long startedAt = System.currentTimeMillis();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", normalizedText);
        input.put("voice", configuration.getVoice());
        input.put("language_type", configuration.getLanguageType());
        JsonNode response = postJson(properties.nativeEndpoint(
                "services/aigc/multimodal-generation/generation"), model,
                Map.of("model", model, "input", input));
        String audioUrl = response.path("output").path("audio").path("url").asText("");
        if (audioUrl.isBlank()) {
            throw failure(model, "DASHSCOPE_INVALID_TTS_RESPONSE",
                    ProviderCallException.Category.INVALID_RESPONSE, false,
                    "语音合成服务未返回音频", null);
        }
        byte[] audio = downloadGeneratedAudio(model, audioUrl);
        Map<String, Object> usage = toMap(response.path("usage"));
        if (response.hasNonNull("request_id")) usage.put("requestId", response.path("request_id").asText());
        return result(audio, model, startedAt, usage);
    }

    public AiCallResult<byte[]> generateImage(String prompt, int width, int height) {
        String model = properties.getImageGeneration().getModel();
        ensureConfigured(model);
        if (prompt == null || prompt.isBlank()) {
            throw failure(model, "EMPTY_IMAGE_PROMPT", ProviderCallException.Category.BAD_REQUEST,
                    false, "图片描述不能为空", null);
        }
        if (width < 512 || height < 512 || (long) width * height > 2048L * 2048L) {
            throw failure(model, "UNSUPPORTED_IMAGE_SIZE", ProviderCallException.Category.UNSUPPORTED_INPUT,
                    false, "图片尺寸不符合模型限制", null);
        }

        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "model", model,
                "input", Map.of("messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of("text", prompt))))),
                "parameters", Map.of(
                        "prompt_extend", true,
                        "n", 1,
                        "size", width + "*" + height,
                        "watermark", false));
        JsonNode response = postJson(properties.nativeEndpoint(
                "services/aigc/multimodal-generation/generation"), model, body);
        String imageUrl = response.path("output").path("choices").path(0)
                .path("message").path("content").path(0).path("image").asText("");
        if (imageUrl.isBlank()) {
            throw failure(model, "DASHSCOPE_INVALID_IMAGE_RESPONSE",
                    ProviderCallException.Category.INVALID_RESPONSE, false,
                    "阿里云图片生成服务未返回图片", null);
        }
        byte[] image = downloadGeneratedImage(model, imageUrl);
        Map<String, Object> usage = toMap(response.path("usage"));
        if (response.hasNonNull("request_id")) usage.put("requestId", response.path("request_id").asText());
        return result(image, model, startedAt, usage);
    }

    private AiCallResult<String> textCompletion(String model, Map<String, Object> body) {
        return textCompletion(model, body, false);
    }

    private AiCallResult<String> textCompletion(String model, Map<String, Object> body, boolean requireComplete) {
        ensureConfigured(model);
        long startedAt = System.currentTimeMillis();
        JsonNode response = postJson(properties.openaiEndpoint("chat/completions"), model, body);
        if (requireComplete) {
            JsonNode choice = response.path("choices").path(0);
            String reason = choice.path("finish_reason").asText("");
            if ("content_filter".equals(reason) || !choice.path("message").path("refusal").asText("").isBlank()) {
                throw failure(model, "LEARNING_OUTPUT_REFUSED", ProviderCallException.Category.CONTENT_SAFETY,
                        false, "本次学习内容未通过模型安全检查，请调整内容后重试。", null);
            }
            if (!"stop".equals(reason)) {
                throw failure(model, "length".equals(reason) ? "LEARNING_OUTPUT_TRUNCATED" : "LEARNING_OUTPUT_INCOMPLETE",
                        ProviderCallException.Category.INVALID_RESPONSE, false,
                        "length".equals(reason) ? "模型输出被截断，内容尚未完整生成。" : "模型未正常完成本次内容输出。", null);
            }
        }
        String content = response.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw failure(model, "DASHSCOPE_EMPTY_RESPONSE",
                    ProviderCallException.Category.INVALID_RESPONSE, false,
                    "阿里云模型未返回有效内容", null);
        }
        return result(content, model, startedAt, toMap(response.path("usage")));
    }

    private JsonNode postJson(String endpoint, String model, Map<String, Object> body) {
        try {
            HttpResponse<String> response = httpClient.send(
                    jsonRequest(endpoint, body, model),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw classify(model, response.statusCode(), response.body(), null);
            }
            return objectMapper.readTree(response.body());
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(model, "DASHSCOPE_INTERRUPTED", ProviderCallException.Category.INTERRUPTED,
                    false, "阿里云模型请求被中断", exception);
        } catch (Exception exception) {
            throw transportFailure(model, exception);
        }
    }

    private HttpRequest jsonRequest(String endpoint, Map<String, Object> body, String model) {
        ensureConfigured(model);
        try {
            Duration timeout = properties.getRequestTimeout() == null
                    ? Duration.ofMinutes(2) : properties.getRequestTimeout();
            return HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(model, "DASHSCOPE_REQUEST_BUILD_FAILED",
                    ProviderCallException.Category.BAD_REQUEST, false,
                    "无法构建阿里云模型请求", exception);
        }
    }

    private byte[] downloadGeneratedImage(String model, String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(Duration.ofMinutes(1)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw classify(model, response.statusCode(), "generated image download failed", null);
            }
            if (response.body() == null || response.body().length == 0 || response.body().length > 25L * 1024 * 1024) {
                throw failure(model, "DASHSCOPE_INVALID_IMAGE_FILE",
                        ProviderCallException.Category.INVALID_RESPONSE, false,
                        "阿里云图片生成结果无效", null);
            }
            return response.body();
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(model, "DASHSCOPE_INTERRUPTED", ProviderCallException.Category.INTERRUPTED,
                    false, "下载生成图片时请求被中断", exception);
        } catch (Exception exception) {
            throw transportFailure(model, exception);
        }
    }

    private byte[] downloadGeneratedAudio(String model, String audioUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(audioUrl))
                    .timeout(Duration.ofMinutes(1)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw classify(model, response.statusCode(), "generated audio download failed", null);
            }
            byte[] audio = response.body();
            if (audio == null || audio.length == 0 || audio.length > 15L * 1024 * 1024) {
                throw failure(model, "DASHSCOPE_INVALID_TTS_FILE",
                        ProviderCallException.Category.INVALID_RESPONSE, false,
                        "语音合成结果无效", null);
            }
            return audio;
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(model, "DASHSCOPE_INTERRUPTED", ProviderCallException.Category.INTERRUPTED,
                    false, "下载语音合成结果时请求被中断", exception);
        } catch (Exception exception) {
            throw transportFailure(model, exception);
        }
    }

    private Map<String, Object> chatMessage(AiChatMessage message) {
        return Map.of("role", message.role(), "content", message.content());
    }

    private String dataUri(byte[] data, String mimeType) {
        String normalized = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
        return "data:" + normalized + ";base64," + Base64.getEncoder().encodeToString(data);
    }

    private void validateImage(byte[] image, String mimeType, String model) {
        ensureConfigured(model);
        if (image == null || image.length == 0) {
            throw failure(model, "EMPTY_IMAGE", ProviderCallException.Category.BAD_REQUEST,
                    false, "图片不能为空", null);
        }
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(mime)) {
            throw failure(model, "UNSUPPORTED_IMAGE_FORMAT", ProviderCallException.Category.UNSUPPORTED_INPUT,
                    false, "图片格式仅支持 JPG、PNG 或 WebP", null);
        }
        if (image.length > 10L * 1024 * 1024) {
            throw failure(model, "IMAGE_TOO_LARGE", ProviderCallException.Category.UNSUPPORTED_INPUT,
                    false, "图片不能超过10MB", null);
        }
    }

    private void ensureConfigured(String model) {
        if (!isConfigured()) {
            throw failure(model, "DASHSCOPE_NOT_CONFIGURED",
                    ProviderCallException.Category.AUTHENTICATION, false,
                    "阿里云百炼尚未配置", null);
        }
    }

    private ProviderCallException classify(String model, int status, String body, Throwable cause) {
        String providerCode = "";
        String providerMessage = "";
        try {
            JsonNode error = objectMapper.readTree(body == null ? "{}" : body);
            providerCode = error.path("code").asText(error.path("error").path("code").asText(""));
            providerMessage = error.path("message").asText(error.path("error").path("message").asText(""));
        } catch (Exception ignored) {
            providerMessage = body == null ? "" : body;
        }
        String normalized = (providerCode + " " + providerMessage).toLowerCase(Locale.ROOT);
        // Quota errors may also use HTTP 429. They must not trigger a paid fallback.
        if (normalized.contains("allocationquota.freetieronly") || normalized.contains("quota")) {
            return failure(model, providerCode(providerCode, "DASHSCOPE_QUOTA_EXHAUSTED"),
                    ProviderCallException.Category.QUOTA_EXHAUSTED, false, "阿里云模型额度不足", cause);
        }
        if (status == 429) {
            return failure(model, providerCode(providerCode, "DASHSCOPE_RATE_LIMITED"),
                    ProviderCallException.Category.RATE_LIMITED, true, "阿里云模型请求过于频繁", cause);
        }
        if (status >= 500) {
            return failure(model, providerCode(providerCode, "DASHSCOPE_UNAVAILABLE"),
                    ProviderCallException.Category.UNAVAILABLE, true, "阿里云模型服务暂时不可用", cause);
        }
        if (normalized.contains("datainspection") || normalized.contains("content") && normalized.contains("safety")) {
            return failure(model, providerCode(providerCode, "DASHSCOPE_CONTENT_SAFETY"),
                    ProviderCallException.Category.CONTENT_SAFETY, false, "请求未通过内容安全检查", cause);
        }
        if (status == 401 || status == 403) {
            return failure(model, providerCode(providerCode, "DASHSCOPE_AUTH_FAILED"),
                    ProviderCallException.Category.AUTHENTICATION, false, "阿里云模型鉴权或权限校验失败", cause);
        }
        return failure(model, providerCode(providerCode, "DASHSCOPE_REQUEST_REJECTED"),
                ProviderCallException.Category.BAD_REQUEST, false,
                providerMessage.isBlank() ? "阿里云模型拒绝了请求" : providerMessage, cause);
    }

    private ProviderCallException transportFailure(String model, Exception exception) {
        Throwable cause = rootCause(exception);
        if (cause instanceof HttpTimeoutException) {
            return failure(model, "DASHSCOPE_TIMEOUT", ProviderCallException.Category.TIMEOUT,
                    true, "阿里云模型请求超时", exception);
        }
        if (cause instanceof ConnectException || cause instanceof java.io.IOException) {
            return failure(model, "DASHSCOPE_UNAVAILABLE", ProviderCallException.Category.UNAVAILABLE,
                    true, "无法连接阿里云模型服务", exception);
        }
        return failure(model, "DASHSCOPE_CLIENT_ERROR", ProviderCallException.Category.UNAVAILABLE,
                true, "阿里云模型调用失败", exception);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null && result.getCause() != result) result = result.getCause();
        return result;
    }

    private ProviderCallException failure(
            String model,
            String code,
            ProviderCallException.Category category,
            boolean retryable,
            String message,
            Throwable cause) {
        return new ProviderCallException(PROVIDER, model, code, category, retryable, message, cause);
    }

    private String providerCode(String providerCode, String fallback) {
        return providerCode == null || providerCode.isBlank() ? fallback : providerCode;
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject()) return new LinkedHashMap<>();
        return objectMapper.convertValue(node, new TypeReference<>() { });
    }

    private <T> AiCallResult<T> result(T value, String model, long startedAt, Map<String, Object> usage) {
        return new AiCallResult<>(value, PROVIDER, model,
                Math.max(0, System.currentTimeMillis() - startedAt), usage);
    }
}
