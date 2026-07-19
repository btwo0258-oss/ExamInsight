package com.example.llm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.Document;
import com.example.llm.entity.Conversation;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.Message;
import com.example.llm.entity.Presentation;
import com.example.llm.entity.PresentationJob;
import com.example.llm.integration.xfyun.XfyunPptClient;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MessageMapper;
import com.example.llm.mapper.PresentationJobMapper;
import com.example.llm.mapper.PresentationMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/presentations")
public class PresentationController {

    private static final String PPT_MIME =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private final PresentationMapper presentationMapper;
    private final PresentationJobMapper jobMapper;
    private final DocumentMapper documentMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final XfyunPptClient pptClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Value("${upload.path}")
    private String uploadPath;

    public PresentationController(
            PresentationMapper presentationMapper,
            PresentationJobMapper jobMapper,
            DocumentMapper documentMapper,
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            XfyunPptClient pptClient,
            ObjectMapper objectMapper) {
        this.presentationMapper = presentationMapper;
        this.jobMapper = jobMapper;
        this.documentMapper = documentMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.pptClient = pptClient;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> listTemplates() {
        return Result.success(List.of(
                template("ink-focus", "墨色重点", "适合课堂讲解与知识梳理", "academic",
                        "#f5f7fa", "#ffffff", "#172033", "#2f6fed"),
                template("clean-grid", "清晰网格", "简洁、克制的信息展示", "minimal",
                        "#f4f4f1", "#ffffff", "#202522", "#16815b"),
                template("bright-ideas", "活力课堂", "适合启发式教学与成果展示", "vibrant",
                        "#fff7e6", "#ffffff", "#29231d", "#ef6c35"),
                template("boardroom", "专业汇报", "适合项目总结与正式陈述", "professional",
                        "#edf1f5", "#ffffff", "#17212b", "#087e8b")
        ));
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        Long userId = requireUserId();
        List<Presentation> items = presentationMapper.selectList(new LambdaQueryWrapper<Presentation>()
                .eq(Presentation::getUserId, userId)
                .orderByDesc(Presentation::getUpdateTime));
        return Result.success(items.stream().map(this::toDto).toList());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable String id) {
        return Result.success(toDto(requireOwned(id, requireUserId())));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        String requestId = requiredString(input, "clientRequestId");
        Presentation existing = presentationMapper.selectOne(new LambdaQueryWrapper<Presentation>()
                .eq(Presentation::getUserId, userId)
                .eq(Presentation::getClientRequestId, requestId)
                .last("LIMIT 1"));
        if (existing != null) return Result.success(toDto(existing));

        Map<String, Object> config = normalizeConfig(input);
        validateKnowledgeBase(userId, longValue(input.get("knowledgeBaseId")));
        LocalDateTime now = LocalDateTime.now();
        Presentation presentation = new Presentation();
        presentation.setId(UUID.randomUUID().toString());
        presentation.setUserId(userId);
        presentation.setStatus("draft");
        presentation.setConfigJson(writeJson(config));
        presentation.setOutlineJson("[]");
        presentation.setPreviewJson("[]");
        presentation.setConversationId(longValue(input.get("conversationId")));
        presentation.setSourceMessageId(stringValue(input.get("sourceMessageId")));
        presentation.setKnowledgeBaseId(longValue(input.get("knowledgeBaseId")));
        presentation.setProjectId(longValue(input.get("projectId")));
        presentation.setLearningResourceId(longValue(input.get("learningResourceId")));
        presentation.setClientRequestId(requestId);
        presentation.setCreateTime(now);
        presentation.setUpdateTime(now);
        presentationMapper.insert(presentation);
        syncConversationCard(presentation);
        return Result.success(toDto(presentation));
    }

    @PutMapping("/{id}/draft")
    public Result<Map<String, Object>> updateDraft(
            @PathVariable String id, @RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        Presentation presentation = requireOwned(id, userId);
        if (List.of("outlining", "generating").contains(presentation.getStatus())) {
            throw new IllegalArgumentException("PPT 正在生成中，暂时不能修改配置");
        }
        Map<String, Object> config = mapValue(input.get("config"));
        presentation.setConfigJson(writeJson(normalizeConfig(config)));
        updateContext(presentation, input, userId);
        presentation.setStatus(readList(presentation.getOutlineJson()).isEmpty() ? "draft" : "outline_ready");
        presentation.setPreviewJson(writeJson(buildPreview(readList(presentation.getOutlineJson()), config)));
        clearError(presentation);
        touch(presentation);
        presentationMapper.updateById(presentation);
        syncConversationCard(presentation);
        return Result.success(toDto(presentation));
    }

    @PutMapping("/{id}/associations")
    public Result<Map<String, Object>> updateAssociations(
            @PathVariable String id, @RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        Presentation presentation = requireOwned(id, userId);
        Long oldKnowledgeBaseId = presentation.getKnowledgeBaseId();
        updateContext(presentation, input, userId);
        touch(presentation);
        presentationMapper.updateById(presentation);
        syncConversationCard(presentation);
        syncDocumentKnowledgeBase(presentation, oldKnowledgeBaseId);
        return Result.success(toDto(presentation));
    }

    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancelDraft(@PathVariable String id) {
        Long userId = requireUserId();
        Presentation presentation = requireOwned(id, userId);
        cancelActiveJob(presentation, userId);
        presentation.setStatus("cancelled");
        presentation.setActiveJobId(null);
        touch(presentation);
        presentationMapper.updateById(presentation);
        syncConversationCard(presentation);
        return Result.success(toDto(presentation));
    }

    @PostMapping("/{id}/outline-jobs")
    public Result<Map<String, Object>> startOutline(
            @PathVariable String id, @RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        Presentation presentation = requireOwned(id, userId);
        String requestId = requiredString(input, "clientRequestId");
        PresentationJob existing = findJobByRequest(userId, "outline", requestId);
        if (existing != null) return Result.success(toJobDto(existing));

        PresentationJob job = newJob(userId, id, "outline", requestId);
        job.setStatus("running");
        job.setProgress(5);
        jobMapper.insert(job);
        presentation.setStatus("outlining");
        presentation.setActiveJobId(job.getJobId());
        clearError(presentation);
        touch(presentation);
        presentationMapper.updateById(presentation);
        executor.execute(() -> runOutline(job.getJobId(), presentation.getId()));
        return Result.success(toJobDto(job));
    }

    @PutMapping("/{id}/outline")
    public Result<Map<String, Object>> updateOutline(
            @PathVariable String id, @RequestBody Map<String, Object> input) {
        Presentation presentation = requireOwned(id, requireUserId());
        List<Map<String, Object>> slides = normalizeSlides(input.get("slides"));
        presentation.setOutlineJson(writeJson(slides));
        presentation.setPreviewJson(writeJson(buildPreview(slides, readMap(presentation.getConfigJson()))));
        presentation.setProviderOutlineJson(writeJson(toProviderOutline(presentation, slides)));
        presentation.setStatus("outline_ready");
        presentation.setActiveJobId(null);
        clearError(presentation);
        touch(presentation);
        presentationMapper.updateById(presentation);
        syncConversationCard(presentation);
        return Result.success(toDto(presentation));
    }

    @PutMapping("/{id}/slides/{slideId}")
    public Result<Map<String, Object>> updateSlide(
            @PathVariable String id,
            @PathVariable String slideId,
            @RequestBody Map<String, Object> input) {
        Presentation presentation = requireOwned(id, requireUserId());
        Map<String, Object> slide = mapValue(input.get("slide"));
        if (!slideId.equals(stringValue(slide.get("id")))) {
            throw new IllegalArgumentException("页面 ID 不一致");
        }
        List<Map<String, Object>> slides = readList(presentation.getOutlineJson());
        boolean replaced = false;
        for (int i = 0; i < slides.size(); i++) {
            if (slideId.equals(stringValue(slides.get(i).get("id")))) {
                slides.set(i, normalizeSlide(slide, i + 1));
                replaced = true;
                break;
            }
        }
        if (!replaced) throw new IllegalArgumentException("PPT 页面不存在");
        presentation.setOutlineJson(writeJson(slides));
        presentation.setPreviewJson(writeJson(buildPreview(slides, readMap(presentation.getConfigJson()))));
        presentation.setProviderOutlineJson(writeJson(toProviderOutline(presentation, slides)));
        touch(presentation);
        presentationMapper.updateById(presentation);
        syncConversationCard(presentation);
        return Result.success(toDto(presentation));
    }

    @PostMapping("/{id}/generation-jobs")
    public Result<Map<String, Object>> startGeneration(
            @PathVariable String id, @RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        Presentation presentation = requireOwned(id, userId);
        if (readList(presentation.getOutlineJson()).isEmpty()) {
            throw new IllegalArgumentException("请先生成并确认 PPT 大纲");
        }
        String requestId = requiredString(input, "clientRequestId");
        PresentationJob existing = findJobByRequest(userId, "generation", requestId);
        if (existing != null) {
            if (canResumeProviderJob(existing)) resumeProviderJob(presentation, existing);
            return Result.success(toJobDto(jobMapper.selectById(existing.getJobId())));
        }

        PresentationJob active = presentation.getActiveJobId() == null
                ? null : jobMapper.selectById(presentation.getActiveJobId());
        if (active != null && List.of("pending", "running").contains(active.getStatus())) {
            return Result.success(toJobDto(active));
        }

        PresentationJob reusable = findReusableProviderJob(userId, id);
        if (reusable != null) {
            resumeProviderJob(presentation, reusable);
            return Result.success(toJobDto(jobMapper.selectById(reusable.getJobId())));
        }

        PresentationJob job = newJob(userId, id, "generation", requestId);
        jobMapper.insert(job);
        presentation.setStatus("generating");
        presentation.setActiveJobId(job.getJobId());
        clearError(presentation);
        touch(presentation);
        presentationMapper.updateById(presentation);
        executor.execute(() -> runGenerationStart(job.getJobId(), presentation.getId()));
        return Result.success(toJobDto(job));
    }

    private void resumeProviderJob(Presentation presentation, PresentationJob job) {
        job.setStatus("running");
        job.setProgress(Math.max(8, job.getProgress() == null ? 0 : job.getProgress()));
        job.setErrorCode("");
        job.setErrorMessage("");
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
        presentation.setStatus("generating");
        presentation.setActiveJobId(job.getJobId());
        presentation.setErrorCode("");
        presentation.setErrorMessage("");
        touch(presentation);
        presentationMapper.updateById(presentation);
        syncConversationCard(presentation);
        executor.execute(() -> refreshGeneration(job));
    }

    private boolean canResumeProviderJob(PresentationJob job) {
        return "generation".equals(job.getType())
                && "failed".equals(job.getStatus())
                && job.getProviderSid() != null
                && !job.getProviderSid().isBlank();
    }

    private PresentationJob findReusableProviderJob(Long userId, String presentationId) {
        PresentationJob job = jobMapper.selectOne(new LambdaQueryWrapper<PresentationJob>()
                .eq(PresentationJob::getUserId, userId)
                .eq(PresentationJob::getPresentationId, presentationId)
                .eq(PresentationJob::getType, "generation")
                .eq(PresentationJob::getStatus, "failed")
                .isNotNull(PresentationJob::getProviderSid)
                .orderByDesc(PresentationJob::getUpdateTime)
                .last("LIMIT 1"));
        return job != null && canResumeProviderJob(job) ? job : null;
    }

    @GetMapping("/jobs/{jobId}")
    public Result<Map<String, Object>> getJob(@PathVariable String jobId) {
        Long userId = requireUserId();
        PresentationJob job = requireOwnedJob(jobId, userId);
        if ("generation".equals(job.getType()) && "running".equals(job.getStatus())) {
            refreshGeneration(job);
            job = requireOwnedJob(jobId, userId);
        }
        return Result.success(toJobDto(job));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public Result<Void> cancelJob(@PathVariable String jobId) {
        Long userId = requireUserId();
        PresentationJob job = requireOwnedJob(jobId, userId);
        if (List.of("pending", "running").contains(job.getStatus())) {
            job.setStatus("cancelled");
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
            Presentation presentation = requireOwned(job.getPresentationId(), userId);
            presentation.setStatus(readList(presentation.getOutlineJson()).isEmpty() ? "draft" : "outline_ready");
            presentation.setActiveJobId(null);
            touch(presentation);
            presentationMapper.updateById(presentation);
            syncConversationCard(presentation);
        }
        return Result.success(null);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) throws Exception {
        Presentation presentation = requireOwned(id, requireUserId());
        if (!"ready".equals(presentation.getStatus()) || presentation.getDocumentId() == null) {
            return ResponseEntity.notFound().build();
        }
        Document document = documentMapper.selectById(presentation.getDocumentId());
        if (document == null || document.getFilePath() == null) return ResponseEntity.notFound().build();
        File file = new File(document.getFilePath());
        if (!file.isFile()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(PPT_MIME))
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new FileSystemResource(file));
    }

    private void runOutline(String jobId, String presentationId) {
        try {
            PresentationJob job = jobMapper.selectById(jobId);
            if (job == null || "cancelled".equals(job.getStatus())) return;
            Presentation presentation = presentationMapper.selectById(presentationId);
            Map<String, Object> config = readMap(presentation.getConfigJson());
            JsonNode response = pptClient.createOutline(outlinePrompt(config), stringValue(config.get("language")));
            JsonNode providerOutline = response.path("outline");
            if (providerOutline.isMissingNode() || providerOutline.isNull()) {
                throw new IllegalStateException("讯飞 PPT 未返回有效大纲");
            }
            List<Map<String, Object>> slides = toSlides(providerOutline, intValue(config.get("pageCount"), 8));
            presentation.setProviderOutlineJson(providerOutline.toString());
            presentation.setOutlineJson(writeJson(slides));
            presentation.setPreviewJson(writeJson(buildPreview(slides, config)));
            presentation.setStatus("outline_ready");
            presentation.setActiveJobId(null);
            clearError(presentation);
            touch(presentation);
            presentationMapper.updateById(presentation);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("presentationId", presentationId);
            result.put("outline", slides);
            job.setStatus("succeeded");
            job.setProgress(100);
            job.setResultJson(writeJson(result));
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (Exception e) {
            failJob(jobId, presentationId, "OUTLINE_GENERATION_FAILED", e);
        }
    }

    private void runGenerationStart(String jobId, String presentationId) {
        try {
            PresentationJob job = jobMapper.selectById(jobId);
            if (job == null || "cancelled".equals(job.getStatus())) return;
            Presentation presentation = presentationMapper.selectById(presentationId);
            Map<String, Object> config = readMap(presentation.getConfigJson());
            List<Map<String, Object>> slides = readList(presentation.getOutlineJson());
            JsonNode providerOutline = objectMapper.valueToTree(toProviderOutline(presentation, slides));
            String providerTemplateId = resolveProviderTemplate(stringValue(config.get("templateId")));
            JsonNode response = pptClient.createPresentation(
                    outlinePrompt(config), providerOutline, providerTemplateId);
            String sid = response.path("sid").asText("");
            if (sid.isBlank()) throw new IllegalStateException("讯飞 PPT 未返回生成任务 ID");
            job = jobMapper.selectById(jobId);
            if (job == null || "cancelled".equals(job.getStatus())) return;
            job.setProviderSid(sid);
            job.setStatus("running");
            job.setProgress(8);
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (Exception e) {
            failJob(jobId, presentationId, "PRESENTATION_START_FAILED", e);
        }
    }

    private synchronized void refreshGeneration(PresentationJob current) {
        PresentationJob job = jobMapper.selectById(current.getJobId());
        if (job == null || !"running".equals(job.getStatus()) || job.getProviderSid() == null) return;
        LocalDateTime now = LocalDateTime.now();
        if (job.getLastProviderPollAt() != null
                && job.getLastProviderPollAt().plusSeconds(3).isAfter(now)) return;
        try {
            JsonNode progress = pptClient.progress(job.getProviderSid());
            job.setLastProviderPollAt(now);
            String status = progress.path("pptStatus").asText("building");
            int total = Math.max(1, progress.path("totalPages").asInt(1));
            int done = Math.max(0, progress.path("donePages").asInt(0));
            job.setProgress(Math.min(92, 8 + (done * 84 / total)));
            job.setUpdateTime(now);
            if ("build_failed".equals(status)) {
                throw new IllegalStateException(progress.path("errMsg").asText("讯飞 PPT 生成失败"));
            }
            if ("done".equals(status)) {
                String url = progress.path("pptUrl").asText("");
                if (url.isBlank()) throw new IllegalStateException("讯飞 PPT 未返回下载地址");
                finishGeneration(job, progress, pptClient.download(url));
            } else {
                jobMapper.updateById(job);
            }
        } catch (Exception e) {
            failJob(job.getJobId(), job.getPresentationId(), "PRESENTATION_GENERATION_FAILED", e);
        }
    }

    private void finishGeneration(PresentationJob job, JsonNode progress, byte[] bytes) throws Exception {
        Presentation presentation = presentationMapper.selectById(job.getPresentationId());
        Map<String, Object> config = readMap(presentation.getConfigJson());
        String title = firstNonBlank(stringValue(config.get("title")), stringValue(config.get("topic")), "演示文稿");
        String fileName = safeFileName(title) + ".pptx";
        File dir = new File(uploadPath, "presentations" + File.separator + presentation.getUserId());
        Files.createDirectories(dir.toPath());
        File target = new File(dir, presentation.getId() + ".pptx");
        Files.write(target.toPath(), bytes);

        Document document = presentation.getDocumentId() == null
                ? null : documentMapper.selectById(presentation.getDocumentId());
        boolean created = document == null;
        if (created) {
            document = new Document();
            document.setUserId(presentation.getUserId());
            document.setKbId(presentation.getKnowledgeBaseId());
            document.setCreateTime(LocalDateTime.now());
        }
        document.setFileName(fileName);
        document.setFileType(PPT_MIME);
        document.setFileSize((long) bytes.length);
        document.setFilePath(target.getAbsolutePath());
        document.setExternalKey("presentation:" + presentation.getId());
        document.setCharCount(0);
        document.setChunkCount(0);
        document.setStatus(1);
        document.setErrorMsg(null);
        document.setUpdateTime(LocalDateTime.now());
        if (created) {
            documentMapper.insert(document);
            adjustKnowledgeBaseCount(presentation.getKnowledgeBaseId(), 1);
        } else {
            documentMapper.updateById(document);
        }

        presentation.setStatus("ready");
        presentation.setActiveJobId(null);
        presentation.setFileName(fileName);
        presentation.setFileSize((long) bytes.length);
        presentation.setDocumentId(document.getId());
        presentation.setResourceId("doc-" + document.getId());
        clearError(presentation);
        touch(presentation);
        presentationMapper.updateById(presentation);
        syncConversationCard(presentation);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("presentationId", presentation.getId());
        result.put("resourceId", presentation.getResourceId());
        result.put("totalPages", progress.path("totalPages").asInt(readList(presentation.getOutlineJson()).size()));
        job.setStatus("succeeded");
        job.setProgress(100);
        job.setResultJson(writeJson(result));
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private void failJob(String jobId, String presentationId, String code, Exception error) {
        PresentationJob job = jobMapper.selectById(jobId);
        if (job != null && !"cancelled".equals(job.getStatus())) {
            job.setStatus("failed");
            job.setErrorCode(code);
            job.setErrorMessage(userMessage(error));
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
        }
        Presentation presentation = presentationMapper.selectById(presentationId);
        if (presentation != null && (job == null || !"cancelled".equals(job.getStatus()))) {
            presentation.setStatus("failed");
            presentation.setActiveJobId(null);
            presentation.setErrorCode(code);
            presentation.setErrorMessage(userMessage(error));
            touch(presentation);
            presentationMapper.updateById(presentation);
            syncConversationCard(presentation);
        }
    }

    private Map<String, Object> toDto(Presentation presentation) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", presentation.getId());
        dto.put("status", presentation.getStatus());
        dto.put("config", readMap(presentation.getConfigJson()));
        dto.put("outline", readList(presentation.getOutlineJson()));
        dto.put("previewPages", readList(presentation.getPreviewJson()));
        dto.put("conversationId", presentation.getConversationId());
        dto.put("sourceMessageId", presentation.getSourceMessageId());
        dto.put("knowledgeBaseId", presentation.getKnowledgeBaseId());
        dto.put("projectId", presentation.getProjectId());
        dto.put("learningResourceId", presentation.getLearningResourceId());
        putIfNotNull(dto, "activeJobId", presentation.getActiveJobId());
        putIfNotNull(dto, "fileName", presentation.getFileName());
        putIfNotNull(dto, "fileSize", presentation.getFileSize());
        putIfNotNull(dto, "resourceId", presentation.getResourceId());
        putIfNotNull(dto, "errorCode", presentation.getErrorCode());
        putIfNotNull(dto, "errorMessage", presentation.getErrorMessage());
        dto.put("createdAt", presentation.getCreateTime().toString());
        dto.put("updatedAt", presentation.getUpdateTime().toString());
        return dto;
    }

    private void syncConversationCard(Presentation presentation) {
        try {
            Map<String, Object> config = readMap(presentation.getConfigJson());
            String topic = stringValue(config.get("topic"));
            if (presentation.getConversationId() != null && topic != null && !topic.isBlank()
                    && !topic.startsWith("待确定")) {
                Conversation conversation = conversationMapper.selectById(presentation.getConversationId());
                if (conversation != null && presentation.getUserId().equals(conversation.getUserId())) {
                    conversation.setTitle(topic.substring(0, Math.min(40, topic.length())));
                    conversation.setUpdateTime(LocalDateTime.now());
                    conversationMapper.updateById(conversation);
                }
            }
            if (presentation.getSourceMessageId() == null || presentation.getSourceMessageId().isBlank()) return;
            Long messageId = Long.valueOf(presentation.getSourceMessageId());
            Message message = messageMapper.selectById(messageId);
            if (message == null || !presentation.getConversationId().equals(message.getConversationId())) return;
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("cardType", "presentation");
            card.put("view", "ready".equals(presentation.getStatus()) ? "result" : "proposal");
            card.put("status", presentation.getStatus());
            card.put("presentationId", presentation.getId());
            card.put("conversationId", presentation.getConversationId());
            card.put("sourceMessageId", presentation.getSourceMessageId());
            card.put("knowledgeBaseId", presentation.getKnowledgeBaseId());
            card.put("projectId", presentation.getProjectId());
            card.put("learningResourceId", presentation.getLearningResourceId());
            card.put("config", config);
            if (presentation.getFileName() != null) card.put("fileName", presentation.getFileName());
            if (presentation.getResourceId() != null) card.put("resourceId", presentation.getResourceId());
            if (presentation.getErrorMessage() != null) card.put("errorMessage", presentation.getErrorMessage());
            card.put("previewPageCount", readList(presentation.getPreviewJson()).size());
            message.setKind("presentation");
            message.setPresentationData(writeJson(card));
            messageMapper.updateById(message);
        } catch (Exception ignored) {
            // Conversation history synchronization must not invalidate a completed PPT operation.
        }
    }

    private Map<String, Object> toJobDto(PresentationJob job) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("jobId", job.getJobId());
        dto.put("status", job.getStatus());
        dto.put("progress", job.getProgress());
        if (job.getResultJson() != null) dto.put("result", readMap(job.getResultJson()));
        putIfNotNull(dto, "errorCode", job.getErrorCode());
        putIfNotNull(dto, "errorMessage", job.getErrorMessage());
        return dto;
    }

    private PresentationJob newJob(Long userId, String presentationId, String type, String requestId) {
        PresentationJob job = new PresentationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setPresentationId(presentationId);
        job.setType(type);
        job.setStatus("pending");
        job.setProgress(0);
        job.setClientRequestId(requestId);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        return job;
    }

    private PresentationJob findJobByRequest(Long userId, String type, String requestId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<PresentationJob>()
                .eq(PresentationJob::getUserId, userId)
                .eq(PresentationJob::getType, type)
                .eq(PresentationJob::getClientRequestId, requestId)
                .last("LIMIT 1"));
    }

    private Presentation requireOwned(String id, Long userId) {
        Presentation presentation = presentationMapper.selectById(id);
        if (presentation == null || !userId.equals(presentation.getUserId())) {
            throw new IllegalArgumentException("PPT 不存在或无权访问");
        }
        return presentation;
    }

    private PresentationJob requireOwnedJob(String id, Long userId) {
        PresentationJob job = jobMapper.selectById(id);
        if (job == null || !userId.equals(job.getUserId())) {
            throw new IllegalArgumentException("PPT 任务不存在或无权访问");
        }
        return job;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        return userId;
    }

    private void updateContext(Presentation presentation, Map<String, Object> input, Long userId) {
        if (input.containsKey("knowledgeBaseId")) {
            Long kbId = longValue(input.get("knowledgeBaseId"));
            validateKnowledgeBase(userId, kbId);
            presentation.setKnowledgeBaseId(kbId);
        }
        if (input.containsKey("conversationId")) presentation.setConversationId(longValue(input.get("conversationId")));
        if (input.containsKey("sourceMessageId")) presentation.setSourceMessageId(stringValue(input.get("sourceMessageId")));
        if (input.containsKey("projectId")) presentation.setProjectId(longValue(input.get("projectId")));
        if (input.containsKey("learningResourceId")) presentation.setLearningResourceId(longValue(input.get("learningResourceId")));
    }

    private void validateKnowledgeBase(Long userId, Long knowledgeBaseId) {
        if (knowledgeBaseId == null) return;
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null || !userId.equals(kb.getUserId()) || !Integer.valueOf(0).equals(kb.getStatus())) {
            throw new IllegalArgumentException("知识库不存在或无权访问");
        }
    }

    private void syncDocumentKnowledgeBase(Presentation presentation, Long oldKbId) {
        if (presentation.getDocumentId() == null) return;
        Document document = documentMapper.selectById(presentation.getDocumentId());
        if (document == null || !presentation.getUserId().equals(document.getUserId())) return;
        Long nextKbId = presentation.getKnowledgeBaseId();
        if (java.util.Objects.equals(document.getKbId(), nextKbId)) return;
        document.setKbId(nextKbId);
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
        adjustKnowledgeBaseCount(oldKbId, -1);
        adjustKnowledgeBaseCount(nextKbId, 1);
    }

    private void adjustKnowledgeBaseCount(Long knowledgeBaseId, int delta) {
        if (knowledgeBaseId == null || delta == 0) return;
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) return;
        kb.setDocCount(Math.max(0, (kb.getDocCount() == null ? 0 : kb.getDocCount()) + delta));
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
    }

    private void cancelActiveJob(Presentation presentation, Long userId) {
        if (presentation.getActiveJobId() == null) return;
        PresentationJob job = jobMapper.selectById(presentation.getActiveJobId());
        if (job != null && userId.equals(job.getUserId())
                && List.of("pending", "running").contains(job.getStatus())) {
            job.setStatus("cancelled");
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
        }
    }

    private List<Map<String, Object>> toSlides(JsonNode providerOutline, int requestedPages) {
        int target = Math.max(3, Math.min(20, requestedPages));
        List<Map<String, Object>> slides = new ArrayList<>();
        List<String> coverPoints = new ArrayList<>();
        String subtitle = providerOutline.path("subTitle").asText("");
        if (!subtitle.isBlank()) coverPoints.add(subtitle);
        slides.add(slide(1, providerOutline.path("title").asText("演示文稿"), coverPoints, "cover"));

        JsonNode chapters = providerOutline.path("chapters");
        if (chapters.isArray()) {
            for (JsonNode chapter : chapters) {
                if (slides.size() >= target) break;
                List<String> points = new ArrayList<>();
                JsonNode children = chapter.path("chapterContents");
                if (children.isArray()) {
                    children.forEach(child -> {
                        String point = child.path("chapterTitle").asText("").trim();
                        if (!point.isBlank()) points.add(point);
                    });
                }
                slides.add(slide(slides.size() + 1,
                        chapter.path("chapterTitle").asText("核心内容"), points, "content"));
            }
        }
        if (slides.size() < target) {
            List<Map<String, Object>> source = new ArrayList<>(slides.subList(1, slides.size()));
            for (Map<String, Object> original : source) {
                for (String point : stringList(original.get("points"))) {
                    if (slides.size() >= target) break;
                    slides.add(slide(slides.size() + 1, point, List.of(stringValue(original.get("title"))), "content"));
                }
                if (slides.size() >= target) break;
            }
        }
        if (slides.size() < target) {
            List<String> summary = slides.stream().skip(1)
                    .map(item -> stringValue(item.get("title"))).filter(value -> value != null && !value.isBlank())
                    .limit(5).toList();
            slides.add(slide(slides.size() + 1, "总结与回顾", summary, "summary"));
        }
        while (slides.size() > target) slides.remove(slides.size() - 1);
        return slides;
    }

    private Map<String, Object> toProviderOutline(Presentation presentation, List<Map<String, Object>> slides) {
        Map<String, Object> config = readMap(presentation.getConfigJson());
        Map<String, Object> outline = new LinkedHashMap<>();
        outline.put("title", firstNonBlank(stringValue(config.get("title")), stringValue(config.get("topic")), "演示文稿"));
        String subtitle = slides.isEmpty() || stringList(slides.get(0).get("points")).isEmpty()
                ? "" : stringList(slides.get(0).get("points")).get(0);
        outline.put("subTitle", subtitle);
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (int i = 1; i < slides.size() && chapters.size() < 20; i++) {
            Map<String, Object> source = slides.get(i);
            Map<String, Object> chapter = new LinkedHashMap<>();
            chapter.put("chapterTitle", firstNonBlank(stringValue(source.get("title")), "核心内容"));
            List<Map<String, Object>> contents = stringList(source.get("points")).stream().map(point -> {
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("chapterTitle", point);
                child.put("chapterContents", null);
                return child;
            }).toList();
            chapter.put("chapterContents", contents);
            chapters.add(chapter);
        }
        outline.put("chapters", chapters);
        return outline;
    }

    private List<Map<String, Object>> normalizeSlides(Object value) {
        List<Map<String, Object>> input = value instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(this::mapValue).toList()
                : List.of();
        if (input.isEmpty()) throw new IllegalArgumentException("PPT 大纲不能为空");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < input.size() && i < 20; i++) result.add(normalizeSlide(input.get(i), i + 1));
        return result;
    }

    private Map<String, Object> normalizeSlide(Map<String, Object> input, int order) {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("id", firstNonBlank(stringValue(input.get("id")), "slide-" + order));
        slide.put("order", order);
        slide.put("title", firstNonBlank(stringValue(input.get("title")), "第 " + order + " 页"));
        slide.put("points", stringList(input.get("points")).stream().limit(8).toList());
        if (input.get("speakerNotes") != null) slide.put("speakerNotes", stringValue(input.get("speakerNotes")));
        slide.put("layout", validLayout(stringValue(input.get("layout")), order == 1 ? "cover" : "content"));
        return slide;
    }

    private Map<String, Object> slide(int order, String title, List<String> points, String layout) {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("id", "slide-" + order);
        slide.put("order", order);
        slide.put("title", title);
        slide.put("points", points);
        slide.put("layout", layout);
        return slide;
    }

    private List<Map<String, Object>> buildPreview(
            List<Map<String, Object>> slides, Map<String, Object> config) {
        Map<String, Object> colors = templateColors(stringValue(config.get("templateId")));
        List<Map<String, Object>> previews = new ArrayList<>();
        for (Map<String, Object> slide : slides) {
            Map<String, Object> preview = new LinkedHashMap<>(slide);
            preview.putAll(colors);
            previews.add(preview);
        }
        return previews;
    }

    private Map<String, Object> normalizeConfig(Map<String, Object> input) {
        String topic = requiredString(input, "topic").trim();
        int pageCount = Math.max(3, Math.min(20, intValue(input.get("pageCount"), 8)));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("topic", topic);
        config.put("title", firstNonBlank(stringValue(input.get("title")), topic));
        config.put("pageCount", pageCount);
        config.put("templateId", validTemplate(stringValue(input.get("templateId"))));
        config.put("aspectRatio", "4:3".equals(input.get("aspectRatio")) ? "4:3" : "16:9");
        config.put("style", validStyle(stringValue(input.get("style"))));
        config.put("audience", validAudience(stringValue(input.get("audience"))));
        config.put("language", firstNonBlank(stringValue(input.get("language")), "zh-CN"));
        copyOptionalList(config, input, "sourceFileNames");
        copyOptionalList(config, input, "mediaAssetIds");
        if (input.get("sourceText") != null) config.put("sourceText", stringValue(input.get("sourceText")));
        return config;
    }

    private String outlinePrompt(Map<String, Object> config) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请围绕“").append(stringValue(config.get("topic"))).append("”生成教学演示文稿，")
                .append("目标约 ").append(intValue(config.get("pageCount"), 8)).append(" 页，")
                .append("受众为 ").append(stringValue(config.get("audience"))).append("，")
                .append("风格为 ").append(stringValue(config.get("style"))).append("。")
                .append("要求结构清晰、每页聚焦一个主题、要点准确且便于复习。");
        String source = stringValue(config.get("sourceText"));
        if (source != null && !source.isBlank()) prompt.append("\n参考资料：\n").append(source);
        return prompt.length() > 12000 ? prompt.substring(0, 12000) : prompt.toString();
    }

    private String resolveProviderTemplate(String businessTemplateId) {
        try {
            JsonNode records = pptClient.listTemplates().path("records");
            if (!records.isArray() || records.isEmpty()) return null;
            int index = Math.floorMod(firstNonBlank(businessTemplateId, "ink-focus").hashCode(), records.size());
            return records.get(index).path("templateIndexId").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> template(String id, String name, String description, String style,
                                         String background, String surface, String text, String accent) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("description", description);
        item.put("style", style);
        item.put("backgroundColor", background);
        item.put("surfaceColor", surface);
        item.put("textColor", text);
        item.put("accentColor", accent);
        return item;
    }

    private Map<String, Object> templateColors(String id) {
        return switch (validTemplate(id)) {
            case "clean-grid" -> colors("#f4f4f1", "#ffffff", "#202522", "#16815b");
            case "bright-ideas" -> colors("#fff7e6", "#ffffff", "#29231d", "#ef6c35");
            case "boardroom" -> colors("#edf1f5", "#ffffff", "#17212b", "#087e8b");
            default -> colors("#f5f7fa", "#ffffff", "#172033", "#2f6fed");
        };
    }

    private Map<String, Object> colors(String background, String surface, String text, String accent) {
        Map<String, Object> colors = new LinkedHashMap<>();
        colors.put("backgroundColor", background);
        colors.put("surfaceColor", surface);
        colors.put("textColor", text);
        colors.put("accentColor", accent);
        return colors;
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("PPT 数据损坏", e);
        }
    }

    private List<Map<String, Object>> readList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<ArrayList<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("PPT 大纲数据损坏", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("PPT 数据序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        return list.stream().map(this::stringValue)
                .filter(item -> item != null && !item.isBlank()).toList();
    }

    private void copyOptionalList(Map<String, Object> target, Map<String, Object> source, String key) {
        List<String> values = stringList(source.get(key));
        if (!values.isEmpty()) target.put(key, values);
    }

    private String requiredString(Map<String, Object> input, String key) {
        String value = stringValue(input.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " 不能为空");
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null || "".equals(value)) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("关联 ID 格式不正确");
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private String validTemplate(String value) {
        return List.of("ink-focus", "clean-grid", "bright-ideas", "boardroom").contains(value)
                ? value : "ink-focus";
    }

    private String validStyle(String value) {
        return List.of("academic", "minimal", "vibrant", "professional").contains(value)
                ? value : "academic";
    }

    private String validAudience(String value) {
        return List.of("student", "teacher", "general", "business").contains(value)
                ? value : "student";
    }

    private String validLayout(String value, String fallback) {
        return List.of("cover", "section", "content", "comparison", "summary").contains(value)
                ? value : fallback;
    }

    private String safeFileName(String value) {
        String safe = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isBlank() ? "演示文稿" : safe.substring(0, Math.min(safe.length(), 120));
    }

    private String userMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "PPT 服务调用失败，请稍后重试" : message;
    }

    private void touch(Presentation presentation) {
        presentation.setUpdateTime(LocalDateTime.now());
    }

    private void clearError(Presentation presentation) {
        presentation.setErrorCode(null);
        presentation.setErrorMessage(null);
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
