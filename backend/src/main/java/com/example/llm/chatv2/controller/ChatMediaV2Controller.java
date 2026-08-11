package com.example.llm.chatv2.controller;

import com.example.llm.chatv2.api.ChatV2ApiException;
import com.example.llm.common.UserContext;
import com.example.llm.integration.ai.AiCallResult;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.integration.ai.ProviderCallException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/v2/media")
public class ChatMediaV2Controller {
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final AiCapabilityRouter ai;
    private final StringRedisTemplate redis;

    public ChatMediaV2Controller(AiCapabilityRouter ai, StringRedisTemplate redis) {
        this.ai = ai;
        this.redis = redis;
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(@RequestPart("file") MultipartFile file) {
        long userId = UserContext.requireSession().userId();
        consume(userId, "asr", 10, Duration.ofMinutes(1));
        consume(userId, "asr-day", 200, Duration.ofDays(1));
        if (file.isEmpty() || file.getSize() > MAX_AUDIO_BYTES) {
            throw new ChatV2ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO",
                    "录音不能为空且不能超过 10 MB。");
        }
        String filename = file.getOriginalFilename() == null ? "voice.wav" : file.getOriginalFilename();
        String extension = extension(filename);
        if (!extension.equals("wav") && !extension.equals("mp3")) {
            throw new ChatV2ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_AUDIO_FORMAT",
                    "当前仅支持 WAV 或 MP3 音频。");
        }
        String mimeType = extension.equals("wav") ? "audio/wav" : "audio/mpeg";
        try {
            AiCallResult<String> result = ai.transcribe(file.getBytes(), mimeType, filename);
            return new TranscriptionResponse(result.value(), "zh-CN");
        } catch (IOException exception) {
            throw new ChatV2ApiException(HttpStatus.BAD_REQUEST, "AUDIO_READ_FAILED", "无法读取录音文件。");
        } catch (ProviderCallException exception) {
            throw providerError(exception, "语音识别暂时不可用，请稍后重试。");
        }
    }

    @PostMapping(value = "/speech", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody SpeechRequest request) {
        long userId = UserContext.requireSession().userId();
        consume(userId, "tts", 20, Duration.ofMinutes(1));
        consume(userId, "tts-day", 500, Duration.ofDays(1));
        try {
            AiCallResult<byte[]> result = ai.synthesizeSpeech(request.text().trim());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=examinsight-speech.wav")
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(result.value());
        } catch (ProviderCallException exception) {
            throw providerError(exception, "朗读服务暂时不可用，请稍后重试。");
        }
    }

    private void consume(long userId, String scope, long limit, Duration window) {
        String key = "chat-v2:media:" + scope + ":" + userId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                throw new ChatV2ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "MEDIA_RATE_LIMITER_UNAVAILABLE", "语音服务暂时不可用，请稍后重试。");
            }
            if (count == 1) redis.expire(key, window);
            if (count > limit) {
                throw new ChatV2ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "MEDIA_RATE_LIMITED", "语音请求过于频繁，请稍后再试。");
            }
        } catch (DataAccessException exception) {
            throw new ChatV2ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MEDIA_RATE_LIMITER_UNAVAILABLE", "语音服务暂时不可用，请稍后重试。");
        }
    }

    private ChatV2ApiException providerError(ProviderCallException exception, String fallback) {
        return switch (exception.category()) {
            case BAD_REQUEST, UNSUPPORTED_INPUT -> new ChatV2ApiException(
                    HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage());
            case CONTENT_SAFETY -> new ChatV2ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, exception.code(), "内容未通过安全检查。");
            case RATE_LIMITED -> new ChatV2ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, exception.code(), "模型请求过于频繁，请稍后再试。");
            case AUTHENTICATION, QUOTA_EXHAUSTED, TIMEOUT, UNAVAILABLE, INVALID_RESPONSE, INTERRUPTED ->
                    new ChatV2ApiException(HttpStatus.SERVICE_UNAVAILABLE, exception.code(), fallback);
        };
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record SpeechRequest(@NotBlank @Size(max = 600) String text) {
    }

    public record TranscriptionResponse(String text, String language) {
    }
}
