package com.example.llm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.MindMapCreateReq;
import com.example.llm.entity.Conversation;
import com.example.llm.entity.Document;
import com.example.llm.entity.GenerationJob;
import com.example.llm.entity.LearningProject;
import com.example.llm.entity.Message;
import com.example.llm.integration.xfyun.XfyunSparkClient;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.GenerationJobMapper;
import com.example.llm.mapper.LearningProjectMapper;
import com.example.llm.mapper.MessageMapper;
import com.example.llm.service.MindMapService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/learning")
public class LearningWorkflowController {

    private final LearningProjectMapper projectMapper;
    private final GenerationJobMapper jobMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final DocumentMapper documentMapper;
    private final MindMapService mindMapService;
    private final LearningProjectController projectController;
    private final XfyunSparkClient sparkClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Value("${upload.path}")
    private String uploadPath;

    public LearningWorkflowController(
            LearningProjectMapper projectMapper,
            GenerationJobMapper jobMapper,
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            DocumentMapper documentMapper,
            MindMapService mindMapService,
            LearningProjectController projectController,
            XfyunSparkClient sparkClient,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.jobMapper = jobMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.documentMapper = documentMapper;
        this.mindMapService = mindMapService;
        this.projectController = projectController;
        this.sparkClient = sparkClient;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @GetMapping("/projects/{projectId}/setup-state")
    public Result<Map<String, Object>> getSetupState(@PathVariable Long projectId) {
        LearningProject project = requireOwned(projectId);
        if (isGeneratedProject(project)) {
            if (project.getSetupStateJson() != null && !project.getSetupStateJson().isBlank()) {
                project.setSetupStateJson("");
                touch(project);
                projectMapper.updateById(project);
            }
            return Result.success(null);
        }
        return Result.success(readNullableMap(project.getSetupStateJson()));
    }

    @PutMapping("/projects/{projectId}/setup-state")
    public Result<Map<String, Object>> saveSetupState(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> state) {
        LearningProject project = requireOwned(projectId);
        if (!List.of("draft", "configuring").contains(project.getStatus())) {
            throw new IllegalArgumentException("当前项目已完成配置，不能覆盖创建状态");
        }
        Map<String, Object> saved = new LinkedHashMap<>(state);
        saved.put("updatedAt", LocalDateTime.now().toString());
        project.setSetupStateJson(writeJson(saved));
        touch(project);
        projectMapper.updateById(project);
        return Result.success(saved);
    }

    @DeleteMapping("/projects/{projectId}/setup-state")
    public Result<Void> removeSetupState(@PathVariable Long projectId) {
        LearningProject project = requireOwned(projectId);
        project.setSetupStateJson("");
        touch(project);
        projectMapper.updateById(project);
        return Result.success(null);
    }

    @GetMapping("/projects/{projectId}/active-plan-generation")
    public Result<Map<String, Object>> getActiveGeneration(@PathVariable Long projectId) {
        LearningProject project = requireOwned(projectId);
        if (isGeneratedProject(project)) {
            if (project.getActiveGenerationJson() != null && !project.getActiveGenerationJson().isBlank()) {
                project.setActiveGenerationJson("");
                touch(project);
                projectMapper.updateById(project);
            }
            return Result.success(null);
        }
        return Result.success(readNullableMap(project.getActiveGenerationJson()));
    }

    @PutMapping("/projects/{projectId}/active-plan-generation")
    public Result<Map<String, Object>> saveActiveGeneration(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> state) {
        LearningProject project = requireOwned(projectId);
        Long draftPlanId = longValue(state.get("draftPlanId"));
        if (!Objects.equals(projectId, draftPlanId)) {
            throw new IllegalArgumentException("draftPlanId 与项目不一致");
        }
        GenerationJob job = requireOwnedJob(requiredText(state, "jobId"));
        if (!Objects.equals(projectId, job.getProjectId())) {
            throw new IllegalArgumentException("生成任务不属于当前项目");
        }
        project.setActiveGenerationJson(writeJson(state));
        touch(project);
        projectMapper.updateById(project);
        return Result.success(state);
    }

    @DeleteMapping("/projects/{projectId}/active-plan-generation")
    public Result<Void> removeActiveGeneration(@PathVariable Long projectId) {
        LearningProject project = requireOwned(projectId);
        project.setActiveGenerationJson("");
        touch(project);
        projectMapper.updateById(project);
        return Result.success(null);
    }

    @GetMapping("/projects/{projectId}/exercise-drafts")
    public Result<List<Map<String, Object>>> getExerciseDrafts(@PathVariable Long projectId) {
        LearningProject project = requireOwned(projectId);
        return Result.success(readMapList(project.getExerciseDraftsJson()));
    }

    @PutMapping("/projects/{projectId}/exercise-drafts/{exerciseId}")
    public Result<Map<String, Object>> saveExerciseDraft(
            @PathVariable Long projectId,
            @PathVariable Long exerciseId,
            @RequestBody Map<String, Object> input) {
        LearningProject project = requireOwned(projectId);
        if (!Objects.equals(exerciseId, longValue(input.get("exerciseId")))) {
            throw new IllegalArgumentException("exerciseId 与路径不一致");
        }
        ensureExercise(project, exerciseId);
        List<Map<String, Object>> drafts = readMapList(project.getExerciseDraftsJson());
        drafts.removeIf(item -> Objects.equals(exerciseId, longValue(item.get("exerciseId"))));
        Map<String, Object> draft = new LinkedHashMap<>(input);
        draft.put("updatedAt", LocalDateTime.now().toString());
        drafts.add(draft);
        project.setExerciseDraftsJson(writeJson(drafts));
        touch(project);
        projectMapper.updateById(project);
        return Result.success(draft);
    }

    @DeleteMapping("/projects/{projectId}/exercise-drafts")
    public Result<Void> removeExerciseDrafts(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> input) {
        LearningProject project = requireOwned(projectId);
        List<Long> ids = valueList(input.get("exerciseIds")).stream().map(this::longValue).toList();
        List<Map<String, Object>> drafts = readMapList(project.getExerciseDraftsJson());
        drafts.removeIf(item -> ids.contains(longValue(item.get("exerciseId"))));
        project.setExerciseDraftsJson(writeJson(drafts));
        touch(project);
        projectMapper.updateById(project);
        return Result.success(null);
    }

    @PostMapping("/profile-jobs")
    public Result<Map<String, Object>> startProfileJob(@RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        Conversation conversation = validateLearningConversation(userId, longValue(input.get("conversationId")));
        Long projectId = conversation == null ? null : conversation.getLearningProjectId();
        if (projectId != null) requireOwned(projectId);
        GenerationJob job = newJob(userId, projectId, "learning-profile");
        jobMapper.insert(job);
        Map<String, Object> request = deepCopy(input);
        executor.execute(() -> runProfileJob(job.getJobId(), request, conversation == null ? null : conversation.getId()));
        return Result.success(toJobDto(job));
    }

    @PostMapping("/profile-confirmations")
    public Result<Map<String, Object>> createConfirmation(@RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        Conversation conversation = validateLearningConversation(userId, longValue(input.get("conversationId")));
        Long projectId = longValue(input.get("projectId"));
        LearningProject project = projectId == null ? null : requireOwned(projectId);
        Map<String, Object> profile = mapValue(input.get("profile"));
        String content = confirmationMarkdown(input, profile);
        Document document = saveMarkdownDocument(
                userId,
                longValue(input.get("knowledgeBaseId")),
                projectId,
                stringValue(input.get("confirmationResourceId")),
                "learning-confirmation:" + requiredText(input, "setupId"),
                firstText(profile.get("subject"), "智能学习") + "-方案确认稿.md",
                content);
        if (project != null) {
            project.setProfile(writeJson(profile));
            project.setGoal(firstText(profile.get("goal"), project.getGoal(), ""));
            project.setStatus("configuring");
            touch(project);
            projectMapper.updateById(project);
        }
        Map<String, Object> result = Map.of("content", content, "resourceId", "doc-" + document.getId());
        if (conversation != null) saveLearningMessage(conversation, "learning-document", Map.of(
                "loading", false, "content", content, "resourceId", "doc-" + document.getId()));
        return Result.success(result);
    }

    @GetMapping("/generation-jobs/{jobId}")
    public Result<Map<String, Object>> getJob(@PathVariable String jobId) {
        return Result.success(toJobDto(requireOwnedJob(jobId)));
    }

    @PostMapping("/plan-jobs")
    public synchronized Result<Map<String, Object>> startPlanJob(@RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        Long projectId = longValue(input.get("draftPlanId"));
        if (projectId == null) throw new IllegalArgumentException("draftPlanId 不能为空");
        LearningProject project = requireOwned(projectId);

        if (isGeneratedProject(project)) {
            GenerationJob completed = jobMapper.selectOne(new LambdaQueryWrapper<GenerationJob>()
                    .eq(GenerationJob::getUserId, userId)
                    .eq(GenerationJob::getProjectId, projectId)
                    .eq(GenerationJob::getType, "learning-plan")
                    .eq(GenerationJob::getStatus, "succeeded")
                    .orderByDesc(GenerationJob::getUpdateTime)
                    .last("LIMIT 1"));
            if (completed == null) {
                completed = newJob(userId, projectId, "learning-plan");
                completed.setStatus("succeeded");
                completed.setProgress(100);
                completed.setResult(writeJson(Map.of("projectId", projectId)));
                jobMapper.insert(completed);
            }
            return Result.success(toJobDto(completed));
        }

        GenerationJob inFlight = jobMapper.selectOne(new LambdaQueryWrapper<GenerationJob>()
                .eq(GenerationJob::getUserId, userId)
                .eq(GenerationJob::getProjectId, projectId)
                .eq(GenerationJob::getType, "learning-plan")
                .in(GenerationJob::getStatus, List.of("pending", "running"))
                .orderByDesc(GenerationJob::getUpdateTime)
                .last("LIMIT 1"));
        if (inFlight != null) return Result.success(toJobDto(inFlight));

        project.setStatus("configuring");
        project.setGoal(firstText(input.get("targetType"), project.getGoal(), ""));
        project.setTargetType(stringValue(input.get("targetType")));
        project.setPeriod(stringValue(input.get("period")));
        project.setDailyTime(stringValue(input.get("dailyTime")));
        project.setWeakPoints(stringValue(input.get("weakPoints")));
        project.setPreferences(writeJson(input.getOrDefault("preferences", List.of())));
        touch(project);
        projectMapper.updateById(project);
        GenerationJob job = newJob(userId, projectId, "learning-plan");
        jobMapper.insert(job);
        Map<String, Object> request = deepCopy(input);
        executor.execute(() -> runPlanJob(job.getJobId(), request));
        return Result.success(toJobDto(job));
    }

    @PostMapping("/projects/{projectId}/activities")
    @Transactional
    public Result<Map<String, Object>> recordActivity(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> input) {
        LearningProject project = requireOwned(projectId);
        if (!Objects.equals(projectId, longValue(input.get("projectId")))) {
            throw new IllegalArgumentException("projectId 与路径不一致");
        }
        Long taskId = longValue(input.get("taskId"));
        Map<String, Object> payload = payload(project);
        Map<String, Object> task = findTask(payload, taskId);
        if (task == null) throw new IllegalArgumentException("学习任务不存在");
        String eventType = requiredText(input, "eventType");
        int progress = intValue(input.get("progress"), intValue(task.get("readProgress"), 0));
        if (progress < 0 || progress > 100) throw new IllegalArgumentException("progress 必须在 0 到 100 之间");
        task.put("readProgress", progress);
        task.put("validStudySeconds", intValue(task.get("validStudySeconds"), 0)
                + Math.max(0, Math.min(300, intValue(input.get("secondsDelta"), 0))));
        if ("complete".equals(eventType) || progress >= 100) {
            task.put("status", "completed");
            task.put("done", true);
        } else if (List.of("start", "reading", "action").contains(eventType)) {
            task.put("status", "in_progress");
        }
        recalculateProject(project, payload);
        savePayload(project, payload);
        return Result.success(projectController.toDto(project));
    }

    @PostMapping("/projects/{projectId}/answers")
    @Transactional
    public Result<Map<String, Object>> submitAnswer(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> input) {
        LearningProject project = requireOwned(projectId);
        if (!Objects.equals(projectId, longValue(input.get("projectId")))) {
            throw new IllegalArgumentException("projectId 与路径不一致");
        }
        Map<String, Object> payload = payload(project);
        Map<String, Object> result = gradeAnswer(project, payload, input);
        savePayload(project, payload);
        removeDraft(project, longValue(input.get("exerciseId")));
        return Result.success(result);
    }

    @PostMapping("/projects/{projectId}/answers/batch")
    @Transactional
    public Result<List<Map<String, Object>>> submitAnswers(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> input) {
        LearningProject project = requireOwned(projectId);
        if (!Objects.equals(projectId, longValue(input.get("projectId")))) {
            throw new IllegalArgumentException("projectId 与路径不一致");
        }
        List<Object> answers = valueList(input.get("answers"));
        if (answers.isEmpty() || answers.size() > 200) throw new IllegalArgumentException("答案数量必须为 1 到 200");
        Map<String, Object> payload = payload(project);
        List<Map<String, Object>> results = new ArrayList<>();
        List<Long> exerciseIds = new ArrayList<>();
        for (Object value : answers) {
            Map<String, Object> answer = mapValue(value);
            Long exerciseId = longValue(answer.get("exerciseId"));
            if (exerciseIds.contains(exerciseId)) throw new IllegalArgumentException("同一题目不能重复提交");
            exerciseIds.add(exerciseId);
            results.add(gradeAnswer(project, payload, answer));
        }
        savePayload(project, payload);
        exerciseIds.forEach(id -> removeDraft(project, id));
        return Result.success(results);
    }

    @PutMapping("/projects/{projectId}/resources/generated")
    public Result<Map<String, Object>> attachGeneratedResource(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> input) {
        LearningProject project = requireOwned(projectId);
        Map<String, Object> payload = payload(project);
        List<Map<String, Object>> resources = mapList(payload.get("resources"));
        Long requestedId = longValue(input.get("learningResourceId"));
        String resourceId = stringValue(input.get("resourceId"));
        String artifactId = stringValue(input.get("artifactId"));
        Map<String, Object> resource = resources.stream().filter(item ->
                requestedId != null && Objects.equals(requestedId, longValue(item.get("id")))
                        || Objects.equals(resourceId, stringValue(item.get("resourceId")))
                        || Objects.equals(artifactId, stringValue(item.get("artifactId")))).findFirst().orElse(null);
        if (resource == null) {
            resource = new LinkedHashMap<>();
            resource.put("id", resources.stream().mapToLong(item -> longValue(item.get("id")) == null ? 0 : longValue(item.get("id"))).max().orElse(0) + 1);
            resources.add(resource);
        }
        resource.put("resourceId", resourceId);
        resource.put("artifactId", artifactId);
        resource.put("title", firstText(input.get("title"), "生成资源"));
        resource.put("fileName", input.get("fileName"));
        resource.put("group", resourceGroup(stringValue(input.get("fileType"))));
        resource.put("desc", "通过智能学习流程生成的项目资源。");
        resource.put("status", "ready");
        resource.put("action", "查看");
        resource.put("source", input.getOrDefault("source", "ai-conversation"));
        resource.put("updatedAt", LocalDateTime.now().toString());
        if (input.get("content") != null) resource.put("content", input.get("content"));
        Map<String, Object> preview = mapValue(input.get("preview"));
        if (preview.get("text") != null) resource.put("content", preview.get("text"));
        if (preview.get("mindMap") != null) {
            resource.put("mindMapTreeData", preview.get("mindMap"));
            resource.put("mindMapRenderConfig", preview.get("mindMapConfig"));
        }
        if (artifactId != null && artifactId.startsWith("mindmap:")) {
            resource.put("mindMapId", longValue(artifactId.substring("mindmap:".length())));
        }
        if (artifactId != null && artifactId.startsWith("presentation:")) {
            resource.put("presentationId", artifactId.substring("presentation:".length()));
        }
        payload.put("resources", resources);
        savePayload(project, payload);
        return Result.success(projectController.toDto(project));
    }

    @GetMapping("/projects/{projectId}/resources/{learningResourceId}/download")
    public ResponseEntity<Resource> downloadProjectResource(
            @PathVariable Long projectId,
            @PathVariable Long learningResourceId) {
        LearningProject project = requireOwned(projectId);
        Map<String, Object> item = findById(mapList(payload(project).get("resources")), learningResourceId);
        if (item == null || !"ready".equals(stringValue(item.get("status")))) {
            return ResponseEntity.notFound().build();
        }
        String fileName = firstText(item.get("fileName"), item.get("title"), "学习资源");
        String resourceId = stringValue(item.get("resourceId"));
        if (resourceId != null && resourceId.matches("doc-\\d+")) {
            Document document = documentMapper.selectById(Long.valueOf(resourceId.substring(4)));
            if (document == null || !project.getUserId().equals(document.getUserId())
                    || document.getFilePath() == null) {
                return ResponseEntity.notFound().build();
            }
            File file = new File(document.getFilePath());
            if (!file.isFile()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(downloadMediaType(document.getFileName(), document.getFileType()))
                    .contentLength(file.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(document.getFileName()))
                    .body(new FileSystemResource(file));
        }

        Object inlineContent = item.get("content");
        if (resourceId != null && resourceId.matches("mindmap-\\d+")) {
            inlineContent = item.get("mindMapTreeData");
            if (!fileName.toLowerCase().endsWith(".json")) fileName += ".json";
        }
        if (inlineContent == null) return ResponseEntity.notFound().build();
        byte[] bytes = inlineContent instanceof String text
                ? text.getBytes(StandardCharsets.UTF_8)
                : writeJson(inlineContent).getBytes(StandardCharsets.UTF_8);
        MediaType mediaType = fileName.toLowerCase().endsWith(".json")
                ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("text/markdown;charset=UTF-8");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(fileName))
                .body(new ByteArrayResource(bytes));
    }

    private void runProfileJob(String jobId, Map<String, Object> input, Long conversationId) {
        GenerationJob job = jobMapper.selectOne(new LambdaQueryWrapper<GenerationJob>()
                .eq(GenerationJob::getJobId, jobId).last("LIMIT 1"));
        if (job == null) return;
        try {
            updateJob(job, "running", 15, null, null, null);
            String prompt = "根据用户学习需求生成学习画像。只返回严格 JSON，不要 Markdown："
                    + "{\"profile\":{\"goal\":\"\",\"subject\":\"\",\"foundation\":\"\","
                    + "\"weakPoints\":[\"\"],\"period\":\"\",\"dailyTime\":\"\","
                    + "\"preferences\":[\"\"],\"source\":\"\",\"extra\":\"\"}}。\n用户输入："
                    + truncate(firstText(input.get("text"), ""), 6000);
            Map<String, Object> generated = callSparkJson(job.getUserId(), prompt);
            Map<String, Object> profile = normalizeProfile(mapValue(generated.get("profile")), input);
            Map<String, Object> result = Map.of("profile", profile);
            updateJob(job, "succeeded", 100, writeJson(result), null, null);
            if (job.getProjectId() != null) {
                LearningProject project = projectMapper.selectById(job.getProjectId());
                if (project != null) {
                    project.setProfile(writeJson(profile));
                    project.setGoal(stringValue(profile.get("goal")));
                    project.setTargetType(stringValue(profile.get("goal")));
                    project.setPeriod(stringValue(profile.get("period")));
                    project.setDailyTime(stringValue(profile.get("dailyTime")));
                    project.setWeakPoints(String.join("、", stringList(profile.get("weakPoints"))));
                    project.setPreferences(writeJson(profile.get("preferences")));
                    project.setStatus("configuring");
                    touch(project);
                    projectMapper.updateById(project);
                }
            }
            Conversation conversation = conversationId == null ? null : conversationMapper.selectById(conversationId);
            if (conversation != null) saveLearningMessage(conversation, "learning-profile", Map.of(
                    "loading", false, "confirmed", false, "profile", profile));
        } catch (Exception error) {
            updateJob(job, "failed", Math.max(15, intValue(job.getProgress(), 0)), null,
                    "LEARNING_PROFILE_FAILED", userMessage(error));
        }
    }

    private void runPlanJob(String jobId, Map<String, Object> input) {
        GenerationJob job = jobMapper.selectOne(new LambdaQueryWrapper<GenerationJob>()
                .eq(GenerationJob::getJobId, jobId).last("LIMIT 1"));
        if (job == null) return;
        try {
            LearningProject project = projectMapper.selectById(job.getProjectId());
            if (project == null) throw new IllegalArgumentException("学习项目不存在");
            if (isGeneratedProject(project)) {
                updateJob(job, "succeeded", 100,
                        writeJson(Map.of("projectId", project.getId())), null, null);
                return;
            }
            updateJob(job, "running", 10, null, null, null);
            String prompt = planPrompt(input, project);
            Map<String, Object> generated = callSparkJson(job.getUserId(), prompt);
            updateJob(job, "running", 55, null, null, null);
            Map<String, Object> payload = buildPlanPayload(project, input, generated);
            project.setPayloadJson(writeJson(payload));
            project.setStatus("ready");
            project.setTotalTasks(countTasks(payload));
            project.setCompletedTasks(0);
            project.setProgress(0);
            project.setSetupStateJson("");
            project.setActiveGenerationJson("");
            touch(project);
            projectMapper.updateById(project);
            updateJob(job, "succeeded", 100, writeJson(Map.of("projectId", project.getId())), null, null);
        } catch (Exception error) {
            LearningProject project = job.getProjectId() == null ? null : projectMapper.selectById(job.getProjectId());
            if (project != null && !isGeneratedProject(project)) {
                project.setStatus("configuring");
                touch(project);
                projectMapper.updateById(project);
            }
            updateJob(job, "failed", Math.max(10, intValue(job.getProgress(), 0)), null,
                    "LEARNING_PLAN_FAILED", userMessage(error));
        }
    }

    private Map<String, Object> buildPlanPayload(
            LearningProject project,
            Map<String, Object> input,
            Map<String, Object> generated) throws Exception {
        List<Map<String, Object>> sourceStages = mapList(generated.get("stages"));
        if (sourceStages.isEmpty()) throw new IllegalStateException("模型未返回有效学习阶段");
        List<Map<String, Object>> stages = new ArrayList<>();
        long nextTaskId = 1;
        long nextStageId = 1;
        for (Map<String, Object> sourceStage : sourceStages.stream().limit(6).toList()) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("id", nextStageId++);
            stage.put("title", firstText(sourceStage.get("title"), "学习阶段"));
            stage.put("desc", firstText(sourceStage.get("desc"), sourceStage.get("goal"), "完成本阶段学习目标"));
            stage.put("scheduleLabel", firstText(sourceStage.get("scheduleLabel"), "按计划完成"));
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (Map<String, Object> sourceTask : mapList(sourceStage.get("tasks")).stream().limit(8).toList()) {
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("id", nextTaskId++);
                task.put("title", firstText(sourceTask.get("title"), "学习任务"));
                task.put("duration", firstText(sourceTask.get("duration"), "30 分钟"));
                task.put("done", false);
                String taskType = validTaskType(stringValue(sourceTask.get("type")));
                task.put("type", taskType);
                task.put("status", "not_started");
                task.put("completionMode", completionMode(taskType));
                task.put("readProgress", 0);
                task.put("validStudySeconds", 0);
                tasks.add(task);
            }
            if (tasks.isEmpty()) throw new IllegalStateException("学习阶段缺少任务");
            stage.put("tasks", tasks);
            stages.add(stage);
        }

        List<Map<String, Object>> exercises = new ArrayList<>();
        long exerciseId = 1;
        for (Map<String, Object> source : mapList(generated.get("exercises")).stream().limit(30).toList()) {
            Map<String, Object> exercise = new LinkedHashMap<>();
            String exerciseType = validExerciseType(stringValue(source.get("type")));
            String explanation = firstText(source.get("explanation"), "请结合学习资料复盘本题。");
            exercise.put("id", exerciseId++);
            exercise.put("title", completeQuestion(
                    firstText(source.get("question"), source.get("stem"), source.get("title"), "练习题"),
                    exerciseType,
                    firstText(source.get("knowledge"), "核心知识"),
                    explanation,
                    stringValue(source.get("answer"))));
            exercise.put("knowledge", firstText(source.get("knowledge"), "核心知识"));
            exercise.put("difficulty", validDifficulty(stringValue(source.get("difficulty"))));
            exercise.put("type", exerciseType);
            exercise.put("options", stringList(source.get("options")));
            exercise.put("answer", requiredGeneratedText(source, "answer"));
            exercise.put("explanation", explanation);
            exercise.put("scene", firstText(source.get("scene"), "practice"));
            exercise.put("cognitiveLevel", firstText(source.get("cognitiveLevel"), "概念理解"));
            exercise.put("purpose", firstText(source.get("purpose"), "阶段练习"));
            exercise.put("submitted", false);
            exercises.add(exercise);
        }
        if (exercises.isEmpty()) throw new IllegalStateException("模型未返回有效练习题");

        String title = firstText(generated.get("title"), project.getTitle());
        String goal = firstText(generated.get("goal"), input.get("targetType"), project.getGoal(), title);
        Map<String, Object> mindMapTree = normalizeMindMap(mapValue(generated.get("mindMap")), title);
        MindMapCreateReq mindMapReq = new MindMapCreateReq();
        mindMapReq.setTitle(title + "知识导图");
        mindMapReq.setKbId(project.getKnowledgeBaseId());
        mindMapReq.setContent(writeJson(mindMapTree));
        Long mindMapId = mindMapService.createMindMap(mindMapReq, project.getUserId());
        String planMarkdown = planMarkdown(title, goal, stages);
        Document planDocument = saveMarkdownDocument(project.getUserId(), project.getKnowledgeBaseId(), project.getId(),
                null, "learning-plan:" + project.getId(), title + "-学习方案.md", planMarkdown);

        List<Map<String, Object>> resources = new ArrayList<>();
        resources.add(resource(1, "学习方案", title + "学习方案", "个性化学习路径与阶段任务。", "ready", "查看",
                title + "-学习方案.md", "doc-" + planDocument.getId()));
        Map<String, Object> mindMapResource = resource(2, "思维导图", title + "知识导图", "核心知识点与学习路径可视化。",
                "ready", "打开", title + "-知识导图.mindmap", "mindmap-" + mindMapId);
        mindMapResource.put("mindMapId", mindMapId);
        mindMapResource.put("mindMapTreeData", mindMapTree);
        mindMapResource.put("mindMapRenderConfig", Map.of("theme", "classic", "layout", "logicalStructure"));
        resources.add(mindMapResource);
        resources.add(resource(3, "PPT", title + "复习 PPT", "根据学习方案生成演示文稿。", "not_selected", "生成",
                title + "-复习.pptx", null));
        resources.add(resource(4, "个性化学习手册", title + "学习手册", "围绕薄弱点组织的详细讲解。", "not_selected", "生成",
                title + "-学习手册.md", null));
        resources.add(resource(5, "代码案例", title + "实践案例", "用于迁移与应用所学知识。", "not_selected", "生成",
                title + "-实践案例.zip", null));
        linkTasks(stages, resources, exercises);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("goal", goal);
        payload.put("profile", profileLabels(input));
        payload.put("stages", stages);
        payload.put("resources", resources);
        payload.put("exercises", exercises);
        payload.put("questionBank", questionBank(input, exercises.size()));
        payload.put("trainingSets", new ArrayList<>());
        payload.put("wrongQuestions", new ArrayList<>());
        payload.put("wrongReviewSets", new ArrayList<>());
        payload.put("dashboard", normalizeDashboard(generated.get("dashboard"), input));
        payload.put("agents", List.of(
                agent("画像分析", "识别学习目标和薄弱点", "done"),
                agent("资料理解", "解析资料库和上传内容", "done"),
                agent("路径规划", "生成阶段化学习路径", "done"),
                agent("资源生成", "创建学习方案与思维导图", "done"),
                agent("效果评估", "根据练习表现持续调整", "running")));
        payload.put("exerciseDone", 0);
        payload.put("totalExercises", exercises.size());
        payload.put("correctRate", 0);
        payload.put("weeklyHours", "0h");
        project.setTitle(title);
        project.setGoal(goal);
        project.setTargetType(firstText(input.get("targetType"), goal));
        project.setPeriod(firstText(input.get("period"), "自主安排"));
        project.setDailyTime(firstText(input.get("dailyTime"), "每天 45 分钟"));
        return payload;
    }

    private String planPrompt(Map<String, Object> input, LearningProject project) {
        return "你是学习路径规划专家。根据确认稿生成可执行的学习方案，只返回严格 JSON，不要 Markdown。"
                + "结构：{\"title\":\"\",\"goal\":\"\",\"stages\":[{\"title\":\"\",\"desc\":\"\","
                + "\"scheduleLabel\":\"\",\"tasks\":[{\"title\":\"\",\"duration\":\"30 分钟\","
                + "\"type\":\"讲解|资料|练习|测验|案例\",\"completionMode\":\"content\"}]}],"
                + "\"exercises\":[{\"title\":\"知识点短标签\",\"question\":\"可独立作答的完整题干，至少20个汉字\",\"knowledge\":\"\",\"difficulty\":\"基础|中等|提高\"," 
                + "\"type\":\"单选题|多选题|判断题|填空题|简答题\",\"options\":[\"A.\"],\"answer\":\"\"," 
                + "\"explanation\":\"\"}],\"mindMap\":{\"data\":{\"text\":\"\"},\"children\":[]},"
                + "\"dashboard\":[{\"label\":\"\",\"value\":50}]}。至少 3 个阶段、每阶段 2 个任务、"
                + "至少 8 道题。每道题必须给出信息完整、语义明确的 question，不能只写知识点名称；"
                + "单选、多选和判断题的 answer 使用选项字母，答案和解析必须准确。\n项目：" + project.getTitle()
                + "\n目标：" + firstText(input.get("targetType"), project.getGoal(), "")
                + "\n周期：" + firstText(input.get("period"), "")
                + "\n基础：" + firstText(input.get("foundation"), "")
                + "\n薄弱点：" + firstText(input.get("weakPoints"), "")
                + "\n确认稿：" + truncate(firstText(input.get("prompt"), ""), 7000);
    }

    private void linkTasks(
            List<Map<String, Object>> stages,
            List<Map<String, Object>> resources,
            List<Map<String, Object>> exercises) {
        List<Map<String, Object>> exerciseTasks = new ArrayList<>();
        Long readingResourceId = resources.stream()
                .filter(item -> List.of("学习方案", "个性化学习手册").contains(stringValue(item.get("group"))))
                .map(item -> longValue(item.get("id")))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        for (Map<String, Object> stage : stages) {
            for (Map<String, Object> task : mapList(stage.get("tasks"))) {
                String type = stringValue(task.get("type"));
                task.put("completionMode", completionMode(type));
                if ("资料".equals(type) && readingResourceId != null) {
                    task.put("learningResourceId", readingResourceId);
                }
                if (List.of("练习", "测验").contains(type)) exerciseTasks.add(task);
            }
        }
        if (exerciseTasks.isEmpty()) return;
        for (int taskIndex = 0; taskIndex < exerciseTasks.size(); taskIndex++) {
            List<Long> ids = new ArrayList<>();
            for (int exerciseIndex = 0; exerciseIndex < exercises.size(); exerciseIndex++) {
                if (exerciseIndex % exerciseTasks.size() == taskIndex) {
                    Long id = longValue(exercises.get(exerciseIndex).get("id"));
                    if (id != null) ids.add(id);
                }
            }
            if (ids.isEmpty() && !exercises.isEmpty()) {
                Long id = longValue(exercises.get(taskIndex % exercises.size()).get("id"));
                if (id != null) ids.add(id);
            }
            exerciseTasks.get(taskIndex).put("exerciseIds", ids);
        }
    }

    private String completionMode(String taskType) {
        return switch (firstText(taskType, "讲解")) {
            case "资料" -> "resource";
            case "练习" -> "exercise";
            case "测验" -> "assessment";
            case "案例" -> "case";
            default -> "content";
        };
    }

    private boolean isGeneratedProject(LearningProject project) {
        return project != null
                && List.of("ready", "in_progress", "completed", "generated", "active")
                        .contains(project.getStatus())
                && project.getPayloadJson() != null
                && !project.getPayloadJson().isBlank();
    }

    private Map<String, Object> normalizeProfile(Map<String, Object> generated, Map<String, Object> input) {
        Map<String, Object> current = mapValue(input.get("currentProfile"));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("goal", firstText(generated.get("goal"), current.get("goal"), input.get("text"), "系统学习"));
        profile.put("subject", firstText(generated.get("subject"), current.get("subject"), input.get("subject"), "综合学习"));
        profile.put("foundation", firstText(generated.get("foundation"), current.get("foundation"), "基础待评估"));
        profile.put("weakPoints", nonEmptyStrings(generated.get("weakPoints"), current.get("weakPoints"), input.get("knowledgeTags")));
        profile.put("period", firstText(generated.get("period"), current.get("period"), "7 天"));
        profile.put("dailyTime", firstText(generated.get("dailyTime"), current.get("dailyTime"), "每天 45 分钟"));
        profile.put("preferences", nonEmptyStrings(generated.get("preferences"), current.get("preferences"), List.of("图文讲解", "练习驱动")));
        profile.put("source", firstText(generated.get("source"), current.get("source"), input.get("source"), "用户输入"));
        profile.put("extra", firstText(generated.get("extra"), current.get("extra"), input.get("supplementalRequirement"), ""));
        return profile;
    }

    private Map<String, Object> callSparkJson(Long userId, String prompt) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "你只输出严格 JSON，禁止输出代码围栏或解释文字。"),
                Map.of("role", "user", "content", prompt));
        String answer = sparkClient.stream(messages, userId, delta -> {});
        return parseJsonObject(answer);
    }

    private Map<String, Object> parseJsonObject(String value) {
        try {
            String json = value.trim();
            if (json.contains("```")) {
                json = json.replaceFirst("(?s)^.*?```(?:json)?\\s*", "").replaceFirst("(?s)```.*$", "");
            }
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) throw new IllegalArgumentException("模型未返回 JSON");
            return objectMapper.readValue(json.substring(start, end + 1),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("模型返回的数据格式不正确", error);
        }
    }

    private Document saveMarkdownDocument(
            Long userId,
            Long knowledgeBaseId,
            Long projectId,
            String existingResourceId,
            String externalKey,
            String fileName,
            String content) {
        try {
            Document document = null;
            if (existingResourceId != null && existingResourceId.matches("doc-\\d+")) {
                document = documentMapper.selectById(Long.valueOf(existingResourceId.substring(4)));
                if (document != null && !userId.equals(document.getUserId())) {
                    throw new IllegalArgumentException("确认稿资源不存在或无权访问");
                }
            }
            if (document == null) {
                document = documentMapper.selectOne(new LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, userId)
                        .eq(Document::getExternalKey, externalKey)
                        .last("LIMIT 1"));
            }
            File directory = new File(uploadPath, "learning" + File.separator + userId);
            Files.createDirectories(directory.toPath());
            String diskName = externalKey.replaceAll("[^a-zA-Z0-9._-]", "_") + ".md";
            File target = new File(directory, diskName);
            Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            LocalDateTime now = LocalDateTime.now();
            boolean created = document == null;
            if (created) {
                document = new Document();
                document.setUserId(userId);
                document.setCreateTime(now);
            }
            document.setKbId(knowledgeBaseId);
            document.setFileName(fileName);
            document.setFileType("text/markdown");
            document.setFileSize(Files.size(target.toPath()));
            document.setFilePath(target.getAbsolutePath());
            document.setExternalKey(externalKey);
            document.setCharCount(content.length());
            document.setChunkCount(0);
            document.setStatus(1);
            document.setErrorMsg(null);
            document.setUpdateTime(now);
            if (created) documentMapper.insert(document); else documentMapper.updateById(document);
            return document;
        } catch (Exception error) {
            if (error instanceof IllegalArgumentException exception) throw exception;
            throw new IllegalStateException("学习文档保存失败", error);
        }
    }

    private void saveLearningMessage(Conversation conversation, String kind, Map<String, Object> data) {
        Message message = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversation.getId())
                .eq(Message::getKind, kind)
                .eq(Message::getStatus, 0)
                .orderByDesc(Message::getCreateTime)
                .last("LIMIT 1"));
        boolean created = message == null;
        if (created) {
            message = new Message();
            message.setConversationId(conversation.getId());
            message.setRole("assistant");
            message.setContent("");
            message.setKind(kind);
            message.setTurnId("learning-" + UUID.randomUUID());
            message.setQVersion(0);
            message.setAVersion(0);
            message.setStatus(0);
            message.setCreateTime(LocalDateTime.now());
        }
        message.setLearningData(writeJson(data));
        if (created) messageMapper.insert(message); else messageMapper.updateById(message);
    }

    private Map<String, Object> gradeAnswer(
            LearningProject project,
            Map<String, Object> payload,
            Map<String, Object> input) {
        Long exerciseId = longValue(input.get("exerciseId"));
        Map<String, Object> exercise = findById(mapList(payload.get("exercises")), exerciseId);
        if (exercise == null) throw new IllegalArgumentException("练习题不存在");
        String userAnswer = firstText(input.get("answer"), "").trim();
        String correctAnswer = firstText(exercise.get("answer"), "").trim();
        boolean correct = isCorrectAnswer(exercise, userAnswer, correctAnswer);
        exercise.put("submitted", true);
        exercise.put("userAnswer", userAnswer);
        exercise.put("gradingCorrect", correct);
        exercise.put("gradingScore", correct ? 100 : 0);
        exercise.put("gradingFeedback", correct ? "回答正确" : "请结合解析复盘相关知识点");
        updateWrongQuestions(payload, exercise, userAnswer, correctAnswer, correct);
        int submitted = (int) mapList(payload.get("exercises")).stream()
                .filter(item -> Boolean.TRUE.equals(item.get("submitted"))).count();
        int correctCount = (int) mapList(payload.get("exercises")).stream()
                .filter(item -> Boolean.TRUE.equals(item.get("gradingCorrect"))).count();
        payload.put("exerciseDone", submitted);
        payload.put("correctRate", submitted == 0 ? 0 : Math.round(correctCount * 100f / submitted));
        recalculateProject(project, payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("correct", correct);
        result.put("score", correct ? 100 : 0);
        result.put("feedback", correct ? "回答正确" : "请复盘相关知识点后再试");
        result.put("explanation", firstText(exercise.get("explanation"), ""));
        result.put("correctAnswer", correctAnswer);
        result.put("taskProgress", 0);
        result.put("projectProgress", project.getProgress());
        return result;
    }

    private void updateWrongQuestions(
            Map<String, Object> payload,
            Map<String, Object> exercise,
            String userAnswer,
            String correctAnswer,
            boolean correct) {
        List<Map<String, Object>> wrongs = mapList(payload.get("wrongQuestions"));
        Long exerciseId = longValue(exercise.get("id"));
        Map<String, Object> wrong = findById(wrongs, exerciseId);
        if (correct) {
            if (wrong != null) wrong.put("status", "mastered");
        } else {
            if (wrong == null) {
                wrong = new LinkedHashMap<>();
                wrong.put("id", exerciseId);
                wrong.put("title", exercise.get("title"));
                wrong.put("knowledge", List.of(firstText(exercise.get("knowledge"), "核心知识")));
                wrong.put("reviewCount", 0);
                wrong.put("correctStreak", 0);
                wrong.put("reviewHistory", new ArrayList<>());
                wrongs.add(wrong);
            }
            wrong.put("userAnswer", userAnswer);
            wrong.put("correctAnswer", correctAnswer);
            wrong.put("reason", "当前答案与正确答案不一致，请结合解析复盘。 ");
            wrong.put("synced", true);
            wrong.put("status", "needs_review");
            wrong.put("errorCount", intValue(wrong.get("errorCount"), 0) + 1);
            wrong.put("lastWrongAt", LocalDateTime.now().toString());
        }
        payload.put("wrongQuestions", wrongs);
    }

    private void recalculateProject(LearningProject project, Map<String, Object> payload) {
        completeSatisfiedExerciseTasks(payload);
        int total = countTasks(payload);
        int done = 0;
        boolean started = false;
        for (Map<String, Object> stage : mapList(payload.get("stages"))) {
            for (Map<String, Object> task : mapList(stage.get("tasks"))) {
                String status = stringValue(task.get("status"));
                if (Boolean.TRUE.equals(task.get("done")) || "completed".equals(status)) done++;
                if (List.of("in_progress", "needs_review", "completed").contains(status)
                        || intValue(task.get("readProgress"), 0) > 0
                        || !stringList(task.get("completedActions")).isEmpty()) {
                    started = true;
                }
            }
        }
        project.setTotalTasks(total);
        project.setCompletedTasks(done);
        project.setProgress(total == 0 ? 0 : Math.round(done * 100f / total));
        project.setStatus(project.getProgress() >= 100 ? "completed" : started ? "in_progress" : "ready");
    }

    private void completeSatisfiedExerciseTasks(Map<String, Object> payload) {
        List<Map<String, Object>> exercises = mapList(payload.get("exercises"));
        for (Map<String, Object> stage : mapList(payload.get("stages"))) {
            for (Map<String, Object> task : mapList(stage.get("tasks"))) {
                String mode = stringValue(task.get("completionMode"));
                if (!List.of("exercise", "assessment").contains(mode)) continue;
                List<Long> ids = valueList(task.get("exerciseIds")).stream()
                        .map(this::longValue)
                        .filter(Objects::nonNull)
                        .toList();
                if (ids.isEmpty()) continue;
                boolean completed = ids.stream().allMatch(id -> {
                    Map<String, Object> exercise = findById(exercises, id);
                    return exercise != null && Boolean.TRUE.equals(exercise.get("submitted"));
                });
                if (completed) {
                    task.put("done", true);
                    task.put("status", "completed");
                    task.put("completionSource", "assessment".equals(mode)
                            ? "已提交阶段测验" : "已提交任务要求的全部练习");
                }
            }
        }
    }

    private void savePayload(LearningProject project, Map<String, Object> payload) {
        project.setPayloadJson(writeJson(payload));
        touch(project);
        projectMapper.updateById(project);
    }

    private void removeDraft(LearningProject project, Long exerciseId) {
        List<Map<String, Object>> drafts = readMapList(project.getExerciseDraftsJson());
        if (drafts.removeIf(item -> Objects.equals(exerciseId, longValue(item.get("exerciseId"))))) {
            project.setExerciseDraftsJson(writeJson(drafts));
            projectMapper.updateById(project);
        }
    }

    private void ensureExercise(LearningProject project, Long exerciseId) {
        if (findById(mapList(payload(project).get("exercises")), exerciseId) == null) {
            throw new IllegalArgumentException("练习题不存在");
        }
    }

    private Map<String, Object> payload(LearningProject project) {
        Map<String, Object> value = readNullableMap(project.getPayloadJson());
        return value == null ? new LinkedHashMap<>() : projectController.normalizeTaskContracts(value);
    }

    private Map<String, Object> findTask(Map<String, Object> payload, Long taskId) {
        for (Map<String, Object> stage : mapList(payload.get("stages"))) {
            Map<String, Object> task = findById(mapList(stage.get("tasks")), taskId);
            if (task != null) return task;
        }
        return null;
    }

    private Map<String, Object> findById(List<Map<String, Object>> list, Long id) {
        return list.stream().filter(item -> Objects.equals(id, longValue(item.get("id")))).findFirst().orElse(null);
    }

    private int countTasks(Map<String, Object> payload) {
        return mapList(payload.get("stages")).stream().mapToInt(stage -> mapList(stage.get("tasks")).size()).sum();
    }

    private Map<String, Object> normalizeMindMap(Map<String, Object> input, String title) {
        Map<String, Object> root = new LinkedHashMap<>(input);
        Map<String, Object> data = mapValue(root.get("data"));
        data.put("text", firstText(data.get("text"), title));
        root.put("data", data);
        root.put("children", mapList(root.get("children")));
        return root;
    }

    private List<Map<String, Object>> profileLabels(Map<String, Object> input) {
        return List.of(
                Map.of("label", "学习目标", "value", firstText(input.get("targetType"), "系统学习")),
                Map.of("label", "当前基础", "value", firstText(input.get("foundation"), "基础待评估")),
                Map.of("label", "重点知识", "value", firstText(input.get("weakPoints"), "按学习过程动态识别")),
                Map.of("label", "时间安排", "value", firstText(input.get("period"), "自主安排") + "，" + firstText(input.get("dailyTime"), "每天 45 分钟")),
                Map.of("label", "学习方式", "value", String.join(" + ", stringList(input.get("preferences")))));
    }

    private Map<String, Object> questionBank(Map<String, Object> input, int generated) {
        int target = Math.max(generated, intValue(input.get("questionCount"), generated));
        return Map.of(
                "targetCount", target,
                "initialCount", generated,
                "generatedCount", generated,
                "difficultyStrategy", "均衡",
                "difficultyCounts", Map.of("basic", generated / 2, "advanced", generated / 3, "challenge", generated - generated / 2 - generated / 3),
                "generatedAt", LocalDateTime.now().toString());
    }

    private List<Map<String, Object>> normalizeDashboard(Object value, Map<String, Object> input) {
        List<Map<String, Object>> dashboard = mapList(value);
        if (!dashboard.isEmpty()) return dashboard.stream().limit(8).toList();
        List<String> weakPoints = List.of(firstText(input.get("weakPoints"), "核心知识").split("[、,，/]"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String point : weakPoints.stream().filter(item -> !item.isBlank()).limit(6).toList()) {
            result.add(Map.of("label", point.trim(), "value", 50));
        }
        return result;
    }

    private Map<String, Object> resource(
            long id, String group, String title, String desc, String status, String action, String fileName, String resourceId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("group", group);
        item.put("title", title);
        item.put("desc", desc);
        item.put("status", status);
        item.put("action", action);
        item.put("fileName", fileName);
        item.put("source", "default");
        if (resourceId != null) item.put("resourceId", resourceId);
        return item;
    }

    private Map<String, Object> agent(String name, String desc, String status) {
        return Map.of("name", name, "desc", desc, "status", status);
    }

    private String confirmationMarkdown(Map<String, Object> input, Map<String, Object> profile) {
        return "# " + firstText(profile.get("subject"), "智能学习") + "学习方案确认稿\n\n"
                + "## 学习目标\n" + firstText(profile.get("goal"), input.get("goal"), "系统学习") + "\n\n"
                + "## 当前基础\n" + firstText(profile.get("foundation"), "基础待评估") + "\n\n"
                + "## 重点与薄弱点\n" + markdownList(stringList(profile.get("weakPoints"))) + "\n\n"
                + "## 时间安排\n- 周期：" + firstText(profile.get("period"), "自主安排")
                + "\n- 每日投入：" + firstText(profile.get("dailyTime"), "每天 45 分钟") + "\n\n"
                + "## 学习偏好\n" + markdownList(stringList(profile.get("preferences"))) + "\n\n"
                + "## 补充要求\n" + firstText(profile.get("extra"), "无") + "\n";
    }

    private String planMarkdown(String title, String goal, List<Map<String, Object>> stages) {
        StringBuilder content = new StringBuilder("# ").append(title).append("学习方案\n\n## 学习目标\n")
                .append(goal).append("\n\n## 学习路径\n");
        for (Map<String, Object> stage : stages) {
            content.append("\n### ").append(stage.get("title")).append("\n").append(stage.get("desc")).append("\n");
            for (Map<String, Object> task : mapList(stage.get("tasks"))) {
                content.append("- ").append(task.get("title")).append("（").append(task.get("duration")).append("）\n");
            }
        }
        return content.toString();
    }

    private String markdownList(List<String> items) {
        return items.isEmpty() ? "- 待后续学习过程补充" : items.stream().map(item -> "- " + item).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private GenerationJob newJob(Long userId, Long projectId, String type) {
        GenerationJob job = new GenerationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setProjectId(projectId);
        job.setType(type);
        job.setStatus("pending");
        job.setProgress(0);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        return job;
    }

    private void updateJob(
            GenerationJob job, String status, int progress, String result, String errorCode, String errorMessage) {
        job.setStatus(status);
        job.setProgress(Math.max(job.getProgress() == null ? 0 : job.getProgress(), Math.min(100, progress)));
        job.setResult(result);
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage);
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private Map<String, Object> toJobDto(GenerationJob job) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("jobId", job.getJobId());
        dto.put("status", job.getStatus());
        dto.put("progress", job.getProgress());
        if (job.getResult() != null) dto.put("result", readNullableMap(job.getResult()));
        if (job.getErrorCode() != null) dto.put("errorCode", job.getErrorCode());
        if (job.getErrorMessage() != null) dto.put("errorMessage", job.getErrorMessage());
        return dto;
    }

    private GenerationJob requireOwnedJob(String jobId) {
        GenerationJob job = jobMapper.selectOne(new LambdaQueryWrapper<GenerationJob>()
                .eq(GenerationJob::getJobId, jobId).last("LIMIT 1"));
        if (job == null || !requireUserId().equals(job.getUserId())) {
            throw new IllegalArgumentException("生成任务不存在或无权访问");
        }
        return job;
    }

    private LearningProject requireOwned(Long projectId) {
        return projectController.requireOwned(projectId);
    }

    private Conversation validateLearningConversation(Long userId, Long conversationId) {
        if (conversationId == null) return null;
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())
                || !"learning-setup".equals(conversation.getConversationType())) {
            throw new IllegalArgumentException("学习配置会话不存在或无权访问");
        }
        return conversation;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        return userId;
    }

    private void touch(LearningProject project) {
        project.setUpdateTime(LocalDateTime.now());
    }

    private Map<String, Object> readNullableMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("学习流程数据损坏", error);
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<ArrayList<Map<String, Object>>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("学习草稿数据损坏", error);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("学习流程数据格式不正确", error);
        }
    }

    private Map<String, Object> deepCopy(Map<String, Object> value) {
        return objectMapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return new LinkedHashMap<>();
        Map<String, Object> map = new LinkedHashMap<>();
        raw.forEach((key, item) -> map.put(String.valueOf(key), item));
        return map;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> values)) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                result.add(map);
            }
        }
        return result;
    }

    private List<Object> valueList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) return new ArrayList<>();
        return values.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }

    private List<String> nonEmptyStrings(Object... values) {
        for (Object value : values) {
            List<String> result = stringList(value);
            if (!result.isEmpty()) return result;
        }
        return List.of("待学习过程进一步识别");
    }

    private String requiredText(Map<String, Object> input, String key) {
        String value = stringValue(input.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " 不能为空");
        return value.trim();
    }

    private String requiredGeneratedText(Map<String, Object> input, String key) {
        String value = stringValue(input.get(key));
        if (value == null || value.isBlank()) throw new IllegalStateException("模型生成的题目缺少" + key);
        return value.trim();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Long.valueOf(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String normalizeAnswer(String value) {
        return value == null ? "" : value.toLowerCase()
                .replaceAll("[\\s，。；：、,.!?！？'\"`（）()【】\\[\\]{}]+", "");
    }

    boolean isCorrectAnswer(Map<String, Object> exercise, String userAnswer, String correctAnswer) {
        String type = stringValue(exercise.get("type"));
        if (List.of("单选题", "多选题", "判断题").contains(type)) {
            Set<String> expected = answerOptionKeys(correctAnswer, stringList(exercise.get("options")));
            Set<String> actual = answerOptionKeys(userAnswer, stringList(exercise.get("options")));
            if (!expected.isEmpty() && !actual.isEmpty()) return expected.equals(actual);
        }
        if (normalizeAnswer(userAnswer).equals(normalizeAnswer(correctAnswer))) return true;
        for (String accepted : stringList(exercise.get("acceptedAnswers"))) {
            if (normalizeAnswer(userAnswer).equals(normalizeAnswer(accepted))) return true;
        }
        return false;
    }

    private Set<String> answerOptionKeys(String answer, List<String> options) {
        Set<String> keys = new TreeSet<>();
        String value = firstText(answer, "").trim();
        if (value.matches("(?i)^[A-Z]+$")) {
            value.toUpperCase().chars().forEach(code -> keys.add(String.valueOf((char) code)));
            return keys;
        }
        for (String part : value.split("\\|\\||[,，;；、\\s]+")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)^\\s*([A-Z])(?:[.．、:：)）\\s]|$)")
                    .matcher(part);
            if (matcher.find()) keys.add(matcher.group(1).toUpperCase());
        }
        if (!keys.isEmpty()) return keys;
        String normalized = normalizeAnswer(value);
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            String withoutLabel = option.replaceFirst("(?i)^\\s*[A-Z][.．、:：)）\\s]*", "");
            if (normalized.equals(normalizeAnswer(option)) || normalized.equals(normalizeAnswer(withoutLabel))) {
                keys.add(String.valueOf((char) ('A' + i)));
            }
        }
        return keys;
    }

    private String completeQuestion(
            String question, String type, String knowledge, String explanation, String answer) {
        String clean = firstText(question, "练习题").replaceFirst("(?i)^Ex-?\\d+\\s*", "").trim();
        if (clean.length() >= 15 || clean.endsWith("？") || clean.endsWith("?")) return clean;
        return switch (type) {
            case "单选题" -> "关于“" + clean + "”，下列说法中正确的是哪一项？";
            case "多选题" -> "关于“" + clean + "”，下列说法中正确的有哪些？";
            case "判断题" -> "A".equalsIgnoreCase(firstText(answer, "")) && explanation.length() >= 8
                    ? "判断下列说法是否正确：" + explanation
                    : "关于“" + clean + "”的下列说法是否正确？";
            case "填空题" -> "请写出“" + clean + "”对应的完整规律、结论或计算关系。";
            case "简答题" -> "请说明“" + clean + "”的解题方法、关键步骤和注意事项。";
            default -> "请完成关于“" + firstText(knowledge, clean) + "”的这道练习题。";
        };
    }

    private MediaType downloadMediaType(String fileName, String fileType) {
        String value = firstText(fileType, "");
        if (value.contains("/")) {
            try {
                return MediaType.parseMediaType(value);
            } catch (Exception ignored) {
            }
        }
        try {
            String detected = Files.probeContentType(new File(fileName).toPath());
            if (detected != null) return MediaType.parseMediaType(detected);
        } catch (Exception ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String attachmentHeader(String fileName) {
        return ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private String validTaskType(String value) {
        return List.of("讲解", "资料", "练习", "测验", "案例").contains(value) ? value : "讲解";
    }

    private String validDifficulty(String value) {
        return List.of("基础", "中等", "提高", "进阶", "挑战").contains(value) ? value : "中等";
    }

    private String validExerciseType(String value) {
        return List.of("单选题", "多选题", "判断题", "填空题", "简答题", "代码题").contains(value) ? value : "单选题";
    }

    private String resourceGroup(String fileType) {
        if ("presentation".equals(fileType)) return "PPT";
        if ("mindmap".equals(fileType)) return "思维导图";
        if ("image".equals(fileType)) return "图片";
        if ("spreadsheet".equals(fileType)) return "电子表格";
        if ("audio".equals(fileType)) return "音频";
        return "文档";
    }

    private String userMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "智能学习生成失败，请稍后重试" : message;
    }
}
