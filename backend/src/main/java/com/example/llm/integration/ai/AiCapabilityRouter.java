package com.example.llm.integration.ai;

import com.example.llm.integration.dashscope.DashScopeAiClient;
import com.example.llm.integration.xfyun.XfyunImageClient;
import com.example.llm.integration.xfyun.XfyunOcrClient;
import com.example.llm.integration.xfyun.XfyunSparkClient;
import com.example.llm.integration.xfyun.XfyunSpeechClient;
import com.example.llm.integration.xfyun.XfyunVisionClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * AI 能力的唯一运行时入口。
 *
 * <p>路由规则由后端控制，不允许业务层或前端直接选择供应商。阿里云为通用能力主路由，
 * 讯飞仅在主路由未配置或发生可重试的基础设施故障时兜底。配额、鉴权、内容安全、
 * 参数错误等非重试错误不会切换供应商，避免意外付费或绕过安全策略。</p>
 */
@Slf4j
@Component
public class AiCapabilityRouter {

    private static final String XFYUN = "xfyun";

    private final DashScopeAiClient dashScope;
    private final XfyunSparkClient spark;
    private final XfyunImageClient image;
    private final XfyunVisionClient vision;
    private final XfyunOcrClient ocr;
    private final XfyunSpeechClient speech;

    public AiCapabilityRouter(
            DashScopeAiClient dashScope,
            XfyunSparkClient spark,
            XfyunImageClient image,
            XfyunVisionClient vision,
            XfyunOcrClient ocr,
            XfyunSpeechClient speech) {
        this.dashScope = dashScope;
        this.spark = spark;
        this.image = image;
        this.vision = vision;
        this.ocr = ocr;
        this.speech = speech;
    }

    public AiCallResult<String> streamChat(
            List<AiChatMessage> messages,
            Long userId,
            Consumer<String> onDelta) {
        if (!dashScope.isConfigured()) {
            return streamWithSpark(messages, userId, onDelta, "primary-not-configured");
        }

        AtomicInteger emittedCharacters = new AtomicInteger();
        Consumer<String> trackedDelta = delta -> {
            if (delta != null) emittedCharacters.addAndGet(delta.length());
            onDelta.accept(delta);
        };
        try {
            AiCallResult<String> result = dashScope.streamChat(messages, trackedDelta);
            logSuccess("chat", result);
            return result;
        } catch (ProviderCallException exception) {
            if (!exception.retryable() || emittedCharacters.get() > 0 || !spark.isConfigured()) throw exception;
            logFallback("chat", exception);
            return streamWithSpark(messages, userId, onDelta, exception.code());
        }
    }

    public AiCallResult<String> completeText(List<AiChatMessage> messages, Long userId) {
        if (!dashScope.isConfigured()) {
            return streamWithSpark(messages, userId, ignored -> { }, "primary-not-configured");
        }
        try {
            AiCallResult<String> result = dashScope.completeText(messages);
            logSuccess("text", result);
            return result;
        } catch (ProviderCallException exception) {
            if (!exception.retryable() || !spark.isConfigured()) throw exception;
            logFallback("text", exception);
            return streamWithSpark(messages, userId, ignored -> { }, exception.code());
        }
    }

    public AiCallResult<String> completeLearningOutput(
            List<AiChatMessage> messages, Long userId, String schemaName, Map<String, Object> schema) {
        // A fallback without schema/finish-reason support cannot honor this contract.
        // Keep the primary error instead of silently accepting a weaker response.
        AiCallResult<String> result = dashScope.completeLearningOutput(messages, schemaName, schema);
        logSuccess(schema == null ? "learning-markdown" : "learning-json", result);
        return result;
    }

    public AiCallResult<String> recognize(byte[] source, String mimeType) {
        if (!dashScope.isConfigured()) return recognizeWithXfyun(source, mimeType, "primary-not-configured");
        try {
            AiCallResult<String> result = dashScope.recognize(source, mimeType);
            logSuccess("ocr", result);
            return result;
        } catch (ProviderCallException exception) {
            if (!exception.retryable()) throw exception;
            logFallback("ocr", exception);
            return recognizeWithXfyun(source, mimeType, exception.code());
        }
    }

    public boolean isRecognitionConfigured() {
        return dashScope.isConfigured() || ocr.isConfigured();
    }

    public AiCallResult<String> understand(byte[] source, String mimeType, String prompt) {
        if (!dashScope.isConfigured()) return understandWithXfyun(source, prompt, "primary-not-configured");
        try {
            AiCallResult<String> result = dashScope.understand(source, mimeType, prompt);
            logSuccess("vision", result);
            return result;
        } catch (ProviderCallException exception) {
            if (!exception.retryable()) throw exception;
            logFallback("vision", exception);
            return understandWithXfyun(source, prompt, exception.code());
        }
    }

    public AiCallResult<String> transcribe(byte[] source, String mimeType, String fileName) {
        if (!dashScope.isConfigured()) return transcribeWithXfyun(source, fileName, "primary-not-configured");
        try {
            AiCallResult<String> result = dashScope.transcribe(source, mimeType);
            logSuccess("asr", result);
            return result;
        } catch (ProviderCallException exception) {
            if (!exception.retryable()) throw exception;
            logFallback("asr", exception);
            return transcribeWithXfyun(source, fileName, exception.code());
        }
    }

    public AiCallResult<byte[]> synthesizeSpeech(String text) {
        AiCallResult<byte[]> result = dashScope.synthesizeSpeech(text);
        logSuccess("tts", result);
        return result;
    }

    public AiCallResult<byte[]> generateImage(String prompt, int width, int height) {
        if (!dashScope.isConfigured()) return generateImageWithXfyun(prompt, width, height, "primary-not-configured");
        try {
            AiCallResult<byte[]> result = dashScope.generateImage(prompt, width, height);
            logSuccess("image", result);
            return result;
        } catch (ProviderCallException exception) {
            if (!exception.retryable() || !image.isConfigured()) throw exception;
            logFallback("image", exception);
            return generateImageWithXfyun(prompt, width, height, exception.code());
        }
    }

    private AiCallResult<String> streamWithSpark(
            List<AiChatMessage> messages,
            Long userId,
            Consumer<String> onDelta,
            String fallbackReason) {
        long startedAt = System.currentTimeMillis();
        List<Map<String, String>> payload = messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
        String value = spark.stream(payload, userId, onDelta);
        AiCallResult<String> result = xfyunResult(
                value, "spark-x", startedAt, fallbackReason);
        logSuccess("chat", result);
        return result;
    }

    private AiCallResult<String> recognizeWithXfyun(
            byte[] source, String mimeType, String fallbackReason) {
        long startedAt = System.currentTimeMillis();
        String value = ocr.recognize(source, xfyunImageEncoding(mimeType));
        AiCallResult<String> result = xfyunResult(value, "xfyun-ocr", startedAt, fallbackReason);
        logSuccess("ocr", result);
        return result;
    }

    private AiCallResult<String> understandWithXfyun(
            byte[] source, String prompt, String fallbackReason) {
        long startedAt = System.currentTimeMillis();
        String value = vision.understand(source, prompt);
        AiCallResult<String> result = xfyunResult(value, "xfyun-vision", startedAt, fallbackReason);
        logSuccess("vision", result);
        return result;
    }

    private AiCallResult<String> transcribeWithXfyun(
            byte[] source, String fileName, String fallbackReason) {
        long startedAt = System.currentTimeMillis();
        String value = speech.transcribe(source, fileName);
        AiCallResult<String> result = xfyunResult(value, "xfyun-iat", startedAt, fallbackReason);
        logSuccess("asr", result);
        return result;
    }

    private AiCallResult<byte[]> generateImageWithXfyun(
            String prompt, int width, int height, String fallbackReason) {
        long startedAt = System.currentTimeMillis();
        byte[] value = image.generate(prompt, width, height);
        AiCallResult<byte[]> result = xfyunResult(
                value, "xfyun-image-generation", startedAt, fallbackReason);
        logSuccess("image", result);
        return result;
    }

    private String xfyunImageEncoding(String mimeType) {
        if (mimeType == null) return "jpg";
        return switch (mimeType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private <T> AiCallResult<T> xfyunResult(
            T value, String model, long startedAt, String fallbackReason) {
        Map<String, Object> usage = new LinkedHashMap<>();
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            usage.put("fallbackReason", fallbackReason);
        }
        return new AiCallResult<>(value, XFYUN, model,
                Math.max(0, System.currentTimeMillis() - startedAt), usage);
    }

    private void logFallback(String capability, ProviderCallException exception) {
        log.warn("AI capability fallback: capability={}, provider={}, model={}, code={}, category={}",
                capability, exception.provider(), exception.model(), exception.code(), exception.category());
    }

    private void logSuccess(String capability, AiCallResult<?> result) {
        log.info("AI capability completed: capability={}, provider={}, model={}, durationMs={}",
                capability, result.provider(), result.model(), result.durationMs());
    }
}
