package com.example.llm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.Conversation;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.MediaAsset;
import com.example.llm.entity.MediaJob;
import com.example.llm.integration.ai.AiCallResult;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.integration.ai.ProviderCallException;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MediaAssetMapper;
import com.example.llm.mapper.MediaJobMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("wav", "mp3");

    private final MediaAssetMapper mediaAssetMapper;
    private final MediaJobMapper mediaJobMapper;
    private final ConversationMapper conversationMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AiCapabilityRouter aiRouter;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();

    @Value("${upload.path}")
    private String uploadPath;

    public MediaController(MediaAssetMapper mediaAssetMapper,
                           MediaJobMapper mediaJobMapper,
                           ConversationMapper conversationMapper,
                           KnowledgeBaseMapper knowledgeBaseMapper,
                           AiCapabilityRouter aiRouter,
                           ObjectMapper objectMapper) {
        this.mediaAssetMapper = mediaAssetMapper;
        this.mediaJobMapper = mediaJobMapper;
        this.conversationMapper = conversationMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.aiRouter = aiRouter;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Map<String, Object>> uploadImage(@RequestPart("file") MultipartFile file,
                                                   @RequestPart("metadata") Map<String, Object> metadata) {
        Long userId = UserContext.getUserId();
        validateContext(userId, metadata);
        String clientRequestId = requiredText(metadata, "clientRequestId");
        MediaAsset existing = findAsset(userId, clientRequestId);
        if (existing != null) return Result.success(toAsset(existing));
        if (file.isEmpty() || file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("图片不能为空且不能超过10MB");
        }
        try {
            byte[] bytes = file.getBytes();
            String mimeType = tika.detect(bytes, file.getOriginalFilename());
            if (!IMAGE_TYPES.contains(mimeType)) throw new IllegalArgumentException("不支持的图片格式: " + mimeType);
            MediaAsset asset = saveAsset(userId, file, bytes, metadata, "image", mimeType, "uploaded", null);
            return Result.success(toAsset(asset));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("图片保存失败", e);
        }
    }

    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> transcribeAudio(@RequestPart("file") MultipartFile file,
                                                       @RequestPart("metadata") Map<String, Object> metadata) {
        Long userId = UserContext.getUserId();
        validateContext(userId, metadata);
        String clientRequestId = requiredText(metadata, "clientRequestId");
        MediaAsset existing = findAsset(userId, clientRequestId);
        if (existing != null && existing.getTranscript() != null) {
            return Result.success(toTranscription(existing, number(metadata.get("durationMs"))));
        }
        if (file.isEmpty() || file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("音频不能为空且不能超过10MB");
        }
        String extension = extension(file.getOriginalFilename());
        if (!AUDIO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("中文识别大模型当前仅支持16kHz单声道WAV或MP3，请转换后重试");
        }
        try {
            byte[] bytes = file.getBytes();
            String mimeType = "wav".equals(extension) ? "audio/wav" : "audio/mpeg";
            AiCallResult<String> transcription = aiRouter.transcribe(
                    bytes, mimeType, file.getOriginalFilename());
            MediaAsset asset = saveAsset(userId, file, bytes, metadata, "audio",
                    mimeType, "ready", transcription.value());
            return Result.success(toTranscription(asset, number(metadata.get("durationMs"))));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("音频保存失败", e);
        }
    }

    @PostMapping("/images/{assetId}/recognition-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<Map<String, Object>> recognizeImage(@PathVariable String assetId,
                                                      @RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        MediaAsset asset = requireAsset(userId, assetId);
        String clientRequestId = requiredText(request, "clientRequestId");
        MediaJob existing = mediaJobMapper.selectOne(new LambdaQueryWrapper<MediaJob>()
                .eq(MediaJob::getUserId, userId)
                .eq(MediaJob::getClientRequestId, clientRequestId)
                .last("LIMIT 1"));
        if (existing != null) return Result.success(toJob(existing));

        String mode = String.valueOf(request.getOrDefault("mode", "auto"));
        MediaJob job = new MediaJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setAssetId(assetId);
        job.setMode(mode);
        job.setStatus("running");
        job.setProgress(15);
        job.setClientRequestId(clientRequestId);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        mediaJobMapper.insert(job);
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(new File(asset.getFilePath()).toPath());
            String prompt = request.get("prompt") instanceof String ? (String) request.get("prompt") : null;
            AiCallResult<String> recognition;
            String intent;
            if ("ocr".equals(mode)) {
                recognition = aiRouter.recognize(bytes, asset.getMimeType());
                intent = "document-ocr";
            } else {
                String resolvedPrompt = "question".equals(mode)
                        ? (prompt == null || prompt.isBlank()
                            ? "请提取图片中的完整题干、选项和公式，保持原有顺序，不要猜测答案" : prompt)
                        : (prompt == null || prompt.isBlank() ? "请识别并说明这张图片的主要内容" : prompt);
                recognition = aiRouter.understand(bytes, asset.getMimeType(), resolvedPrompt);
                intent = "question".equals(mode) ? "question-capture" : "general-image";
            }
            Map<String, Object> result = new HashMap<>();
            result.put("assetId", assetId);
            result.put("mode", mode);
            result.put("text", recognition.value());
            result.put("intent", intent);
            result.put("provider", recognition.provider());
            result.put("model", recognition.model());
            result.put("durationMs", recognition.durationMs());
            job.setResultJson(objectMapper.writeValueAsString(result));
            job.setStatus("succeeded");
            job.setProgress(100);
            asset.setStatus("ready");
            asset.setUpdateTime(LocalDateTime.now());
            mediaAssetMapper.updateById(asset);
        } catch (Exception e) {
            job.setStatus("failed");
            job.setProgress(100);
            job.setErrorCode(e instanceof ProviderCallException providerError
                    ? providerError.code() : "IMAGE_RECOGNITION_REJECTED");
            job.setErrorMessage(e.getMessage());
        }
        job.setUpdateTime(LocalDateTime.now());
        mediaJobMapper.updateById(job);
        return Result.success(toJob(job));
    }

    @GetMapping("/jobs/{jobId}")
    public Result<Map<String, Object>> getJob(@PathVariable String jobId) {
        MediaJob job = mediaJobMapper.selectById(jobId);
        if (job == null || !UserContext.getUserId().equals(job.getUserId())) {
            throw new IllegalArgumentException("图片识别任务不存在");
        }
        return Result.success(toJob(job));
    }

    private MediaAsset saveAsset(Long userId, MultipartFile file, byte[] bytes, Map<String, Object> metadata,
                                 String kind, String mimeType, String status, String transcript) throws java.io.IOException {
        String id = UUID.randomUUID().toString();
        File directory = new File(uploadPath, "media/" + userId);
        if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("无法创建媒体目录");
        String originalName = file.getOriginalFilename() == null ? kind : new File(file.getOriginalFilename()).getName();
        File target = new File(directory, id + "." + extension(originalName));
        java.nio.file.Files.write(target.toPath(), bytes);

        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setUserId(userId);
        asset.setKind(kind);
        asset.setSource(String.valueOf(metadata.getOrDefault("source", "upload")));
        asset.setPurpose(String.valueOf(metadata.getOrDefault("purpose", "chat-attachment")));
        asset.setFileName(originalName);
        asset.setMimeType(mimeType);
        asset.setFilePath(target.getAbsolutePath());
        asset.setSize((long) bytes.length);
        asset.setStatus(status);
        asset.setConversationId(number(metadata.get("conversationId")));
        asset.setKbId(number(metadata.get("knowledgeBaseId")));
        asset.setProjectId(number(metadata.get("projectId")));
        asset.setTranscript(transcript);
        asset.setClientRequestId(requiredText(metadata, "clientRequestId"));
        asset.setCreateTime(LocalDateTime.now());
        asset.setUpdateTime(LocalDateTime.now());
        mediaAssetMapper.insert(asset);
        return asset;
    }

    private void validateContext(Long userId, Map<String, Object> metadata) {
        Long conversationId = number(metadata.get("conversationId"));
        Long kbId = number(metadata.get("knowledgeBaseId"));
        String purpose = String.valueOf(metadata.getOrDefault("purpose", "chat-attachment"));
        if ("chat-attachment".equals(purpose) && conversationId == null) {
            throw new IllegalArgumentException("聊天附件必须提供conversationId");
        }
        if (conversationId != null) {
            Conversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !userId.equals(conversation.getUserId())) {
                throw new IllegalArgumentException("无权访问该对话");
            }
        }
        if (kbId != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
            if (kb == null || !userId.equals(kb.getUserId()) || kb.getStatus() != 0) {
                throw new IllegalArgumentException("无权访问该知识库");
            }
        }
    }

    private Map<String, Object> readMetadata(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("metadata格式错误");
        }
    }

    private MediaAsset findAsset(Long userId, String clientRequestId) {
        return mediaAssetMapper.selectOne(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getUserId, userId)
                .eq(MediaAsset::getClientRequestId, clientRequestId)
                .last("LIMIT 1"));
    }

    private MediaAsset requireAsset(Long userId, String id) {
        MediaAsset asset = mediaAssetMapper.selectById(id);
        if (asset == null || !userId.equals(asset.getUserId())) throw new IllegalArgumentException("媒体资源不存在");
        return asset;
    }

    private Map<String, Object> toAsset(MediaAsset asset) {
        Map<String, Object> value = new HashMap<>();
        value.put("id", asset.getId());
        value.put("kind", asset.getKind());
        value.put("source", asset.getSource());
        value.put("purpose", asset.getPurpose());
        value.put("fileName", asset.getFileName());
        value.put("mimeType", asset.getMimeType());
        value.put("size", asset.getSize());
        value.put("status", asset.getStatus());
        value.put("conversationId", asset.getConversationId());
        value.put("knowledgeBaseId", asset.getKbId());
        value.put("projectId", asset.getProjectId());
        value.put("createdAt", iso(asset.getCreateTime()));
        value.put("updatedAt", iso(asset.getUpdateTime()));
        if (asset.getErrorCode() != null) value.put("errorCode", asset.getErrorCode());
        if (asset.getErrorMessage() != null) value.put("errorMessage", asset.getErrorMessage());
        return value;
    }

    private Map<String, Object> toTranscription(MediaAsset asset, Long durationMs) {
        Map<String, Object> value = new HashMap<>();
        value.put("asset", toAsset(asset));
        value.put("text", asset.getTranscript());
        value.put("language", "zh-CN");
        value.put("durationMs", durationMs == null ? 0 : durationMs);
        return value;
    }

    private Map<String, Object> toJob(MediaJob job) {
        Map<String, Object> value = new HashMap<>();
        value.put("jobId", job.getId());
        value.put("status", job.getStatus());
        value.put("progress", job.getProgress());
        if (job.getResultJson() != null) {
            try {
                Map<String, Object> result = objectMapper.readValue(
                        job.getResultJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                result.remove("provider");
                result.remove("model");
                result.remove("durationMs");
                value.put("result", result);
            } catch (Exception ignored) {
            }
        }
        if (job.getErrorCode() != null) value.put("errorCode", job.getErrorCode());
        if (job.getErrorMessage() != null) value.put("errorMessage", job.getErrorMessage());
        return value;
    }

    private String requiredText(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String) || ((String) raw).isBlank()) throw new IllegalArgumentException(key + "不能为空");
        return ((String) raw).trim();
    }

    private Long number(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) return "bin";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private String iso(LocalDateTime value) {
        return value == null ? "" : value.atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
