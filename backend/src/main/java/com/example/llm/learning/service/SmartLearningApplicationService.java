package com.example.llm.learning.service;

import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.chatv2.repository.ChatV2Repository;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.integration.ai.AiChatMessage;
import com.example.llm.learning.api.SmartLearningDtos;
import com.example.llm.learning.repository.SmartLearningRepository;
import com.example.llm.learning.repository.SmartLearningRepository.ChunkRecord;
import com.example.llm.learning.repository.SmartLearningRepository.JobRecord;
import com.example.llm.learning.repository.SmartLearningRepository.ProjectRecord;
import com.example.llm.learning.repository.SmartLearningRepository.TaskRecord;
import com.example.llm.learning.repository.SmartLearningRepository.ResourceRecord;
import com.example.llm.learning.repository.SmartLearningRepository.ExecutionRecord;
import com.example.llm.learning.repository.SmartLearningRepository.TutorThreadRecord;
import com.example.llm.learning.repository.SmartLearningRepository.TutorSidebarRecord;
import com.example.llm.learning.repository.SmartLearningRepository.WrongItemRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SmartLearningApplicationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_PROJECT_ICON = "notebook";
    private static final String DEFAULT_PROJECT_ICON_COLOR = "#667085";
    private final SmartLearningRepository repository;
    private final AiCapabilityRouter ai;
    private final ObjectMapper objectMapper;
    private final AuthCrypto crypto;
    private final ChatV2Repository chatRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "smart-learning-job");
        thread.setDaemon(true);
        return thread;
    });
    private final ThreadLocal<String> currentJobId = new ThreadLocal<>();

    public SmartLearningApplicationService(
            SmartLearningRepository repository,
            AiCapabilityRouter ai,
            ObjectMapper objectMapper,
            AuthCrypto crypto,
            ChatV2Repository chatRepository) {
        this.repository = repository;
        this.ai = ai;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
        this.chatRepository = chatRepository;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public List<SmartLearningDtos.ProjectSummary> list(long userId) {
        return repository.findProjects(userId).stream().map(project -> summary(userId, project)).toList();
    }

    public SmartLearningDtos.ProjectDetail create(
            long userId,
            SmartLearningDtos.CreateProjectRequest request) {
        String name = text(request.name());
        if (name.isBlank() || name.length() > 160) {
            throw new IllegalArgumentException("项目名称不能为空，且不能超过 160 个字符。");
        }
        String kb = blankToNull(request.knowledgeBaseId());
        if (kb != null && !repository.ownsActiveKnowledgeBase(userId, kb)) {
            throw new IllegalArgumentException("所选知识库不存在或无权访问。");
        }
        String projectId = repository.createProject(
                userId,
                name,
                checkedIcon(defaultText(request.icon(), DEFAULT_PROJECT_ICON)),
                checkedColor(defaultText(request.iconColor(), DEFAULT_PROJECT_ICON_COLOR)),
                kb);
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail detail(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        JobRecord activeJob = repository.findLatestJob(userId, projectId).orElse(null);
        return toDetail(project, activeJob);
    }

    public SmartLearningDtos.ProjectDetail rename(
            long userId,
            String projectId,
            SmartLearningDtos.RenameRequest request) {
        String name = text(request.name());
        if (name.isBlank() || name.length() > 160) {
            throw new IllegalArgumentException("项目名称不能为空，且不能超过 160 个字符。");
        }
        requireProject(userId, projectId);
        repository.updateProjectAppearance(userId, projectId, name, checkedIcon(request.icon()), checkedColor(request.iconColor()));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail pin(
            long userId, String projectId, SmartLearningDtos.PinRequest request) {
        requireProject(userId, projectId);
        repository.setProjectPinned(userId, projectId, request.pinned());
        return detail(userId, projectId);
    }

    public List<SmartLearningDtos.SidebarProjectView> sidebar(long userId) {
        Map<String, List<SmartLearningDtos.TutorConversationView>> conversations = new LinkedHashMap<>();
        for (TutorSidebarRecord item : repository.findTutorConversations(userId)) {
            conversations.computeIfAbsent(item.projectExternalId(), ignored -> new ArrayList<>())
                    .add(new SmartLearningDtos.TutorConversationView(
                            item.conversationExternalId(), defaultText(item.title(), "AI 助教"),
                            item.taskExternalId(), item.taskTitle(), item.contextType(), item.updatedAt()));
        }
        List<ProjectRecord> projects = new ArrayList<>(repository.findProjects(userId));
        projects.sort(Comparator
                .comparing((ProjectRecord project) -> project.pinnedAt() == null)
                .thenComparing(ProjectRecord::pinnedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ProjectRecord::updatedAt, Comparator.reverseOrder()));
        return projects.stream().map(project -> new SmartLearningDtos.SidebarProjectView(
                project.externalId(), project.name(), project.icon(), project.iconColor(), project.stage(), project.pinnedAt(),
                conversations.getOrDefault(project.externalId(), List.of()), project.updatedAt())).toList();
    }

    public void deletePermanently(long userId, String projectId) {
        requireProject(userId, projectId);
        for (String conversationId : repository.findTutorConversationIds(userId, projectId)) {
            try {
                chatRepository.moveConversationToTrash(userId, conversationId);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("该项目仍有 AI 助教回答正在生成，请停止生成后再删除。", exception);
            }
        }
        repository.deleteProjectPermanently(userId, projectId);
    }

    public void archive(long userId, String projectId) {
        requireProject(userId, projectId);
        repository.archiveProject(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail restore(long userId, String projectId) {
        repository.findArchivedProject(userId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在或无法恢复。"));
        repository.restoreProject(userId, projectId);
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail saveTarget(
            long userId, String projectId, Map<String, Object> target) {
        requireProject(userId, projectId);
        repository.saveTargetDraft(userId, projectId, json(normalizedTarget(target)));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail confirmTarget(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectNotArchived(project);
        validateTarget(project.targetDraft());
        repository.confirmTarget(userId, projectId);
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail saveSources(
            long userId,
            String projectId,
            String knowledgeBaseId,
            Map<String, Object> sources) {
        requireProject(userId, projectId);
        String kb = blankToNull(knowledgeBaseId);
        if (kb != null && !repository.ownsActiveKnowledgeBase(userId, kb)) {
            throw new IllegalArgumentException("所选知识库不存在或无权访问。");
        }
        Map<String, Object> normalized = normalizedSources(sources);
        validateAssetOwnership(userId, normalized);
        repository.saveSourcesDraft(userId, projectId, kb, json(normalized));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail confirmSources(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "SOURCES_REQUIRED");
        Map<String, Object> sources = project.sourcesDraft();
        if (sources.isEmpty()) throw new IllegalArgumentException("请先选择资料，或明确选择手动填写范围。");
        boolean hasAssets = !assetIds(sources).isEmpty();
        boolean manual = !text(sources.get("manualScope")).isBlank();
        if (!hasAssets && !manual) {
            throw new IllegalArgumentException("请先选择资料，或明确选择手动填写范围。");
        }
        validateAssetOwnership(userId, sources);
        repository.confirmSources(userId, projectId);
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail saveScopeCandidate(
            long userId, String projectId, Map<String, Object> scope) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "SCOPE_REQUIRED");
        validateDraftList(scope, "nodes");
        repository.saveScopeCandidate(userId, projectId, json(scope));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail confirmScope(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "SCOPE_REQUIRED");
        if (project.scopeCandidate().isEmpty()) throw new IllegalArgumentException("请先生成或编辑学习范围。");
        validateScope(project.scopeCandidate());
        repository.confirmScope(userId, projectId);
        return detail(userId, projectId);
    }

    public SmartLearningDtos.JobAccepted generateScope(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStage(project, "SCOPE_REQUIRED");
        Map<String, Object> input = Map.of("sources", project.sources(), "target", project.target());
        return startJob(userId, project, "SCOPE_ANALYSIS", input, () -> {
            List<ChunkRecord> chunks = repository.findSourceChunks(userId, project.sources());
            String manual = text(project.sources().get("manualScope"));
            if (chunks.isEmpty() && manual.isBlank()) {
                throw new IllegalStateException("所选资料还没有可用的解析内容，请处理完成后重试。");
            }
            List<List<ChunkRecord>> batches = partitionScopeBatches(chunks);
            if (batches.isEmpty()) batches = List.of(List.of());
            List<Map<String, Object>> mergedNodes = new ArrayList<>();
            for (int index = 0; index < batches.size(); index++) {
                String batchManual = index == batches.size() - 1 ? limit(manual, 5000) : "";
                String source = formatScopeSource(batches.get(index), batchManual);
                Map<String, Object> batch = parseModel(callModel(
                        "你是学习范围分析助手。这是第 " + (index + 1) + " / " + batches.size() + " 个资料批次。只返回 JSON 对象，格式为 {\"nodes\":[{\"id\":\"n1\",\"title\":\"知识点\",\"parentId\":null,\"priority\":\"核心\",\"reason\":\"依据\",\"evidence\":[{\"assetId\":\"资料ID\",\"locator\":\"章节或页码\"}]}]}. " +
                                "只根据本批资料和用户手动范围整理知识点，不得编造事实；批次资料不足时在 reason 中说明。\n资料内容：" + source,
                        userId));
                ensureList(batch, "nodes", "模型没有返回有效的学习范围。");
                filterScopeEvidence(batch, assetIds(project.sources()));
                validateScope(batch);
                mergedNodes.addAll(prefixScopeNodes(batch, index + 1));
            }
            Map<String, Object> generated = new LinkedHashMap<>();
            generated.put("nodes", mergedNodes);
            generated.put("sourceVersion", project.sourceVersion());
            generated.put("generatedAt", LocalDateTime.now(ZoneOffset.UTC).toString());
            validateScope(generated);
            ensureJobInputsUnchanged(userId, project, "SCOPE_REQUIRED");
            repository.saveScopeCandidate(userId, project.externalId(), json(generated));
            return generated;
        });
    }

    public SmartLearningDtos.JobAccepted generateDiagnosis(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStage(project, "DIAGNOSTIC_REQUIRED");
        Map<String, Object> input = Map.of("scope", project.scope(), "scopeVersion", project.scopeVersion());
        return startJob(userId, project, "DIAGNOSIS_GENERATION", input, () -> {
            Map<String, Object> generated = parseModel(callModel(
                    "你是学习诊断出题助手。只返回 JSON 对象，格式为 {\"questions\":[{\"id\":\"q1\",\"conceptId\":\"n1\",\"type\":\"single_choice\",\"stem\":\"题目\",\"options\":[\"A\",\"B\"],\"answer\":\"A\",\"explanation\":\"判定依据\"}]}. " +
                            "题目必须覆盖给定范围；answer 和 explanation 仅用于服务端判分，不能放入返回给用户的题目界面。\n确认范围：" + json(project.scope()),
                    userId));
            ensureList(generated, "questions", "模型没有返回有效的诊断题目。");
            generated.put("scopeVersion", project.scopeVersion());
            generated.put("generatedAt", LocalDateTime.now(ZoneOffset.UTC).toString());
            validateDiagnosisCandidate(generated);
            ensureJobInputsUnchanged(userId, project, "DIAGNOSTIC_REQUIRED");
            repository.saveDiagnosisCandidate(userId, project.externalId(), json(generated));
            return generated;
        });
    }

    public SmartLearningDtos.ProjectDetail submitDiagnosis(
            long userId, String projectId, Map<String, Object> body) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "DIAGNOSTIC_REQUIRED");
        if (project.diagnosisCandidate().isEmpty()) throw new IllegalArgumentException("请先生成诊断题目。");
        Map<String, Object> raw = repository.findProject(userId, projectId)
                .orElseThrow().diagnosisCandidate();
        Map<String, Object> result = gradeDiagnosis(raw, body.get("answers"));
        result.put("scopeVersion", project.scopeVersion());
        result.put("submittedAt", LocalDateTime.now(ZoneOffset.UTC).toString());
        repository.confirmDiagnosis(userId, projectId, json(result));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail saveDiagnosisAnswers(
            long userId, String projectId, Map<String, Object> body) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "DIAGNOSTIC_REQUIRED");
        Object answers = body == null ? null : body.get("answers");
        if (!(answers instanceof List<?>)) {
            throw new IllegalArgumentException("诊断答案格式不正确。");
        }
        if (project.diagnosisCandidate().isEmpty() && !((List<?>) answers).isEmpty()) {
            throw new IllegalArgumentException("请先生成诊断题目。");
        }
        repository.saveDiagnosisAnswersDraft(userId, projectId, json(Map.of(
                "answers", answers, "skipReason", limit(text(body.get("skipReason")), 500),
                "skipRequested", Boolean.TRUE.equals(body.get("skipRequested")))));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail skipDiagnosis(
            long userId, String projectId, Map<String, Object> body) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "DIAGNOSTIC_REQUIRED");
        String reason = text(body.get("reason"));
        if (reason.isBlank()) throw new IllegalArgumentException("请说明跳过诊断的原因。");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skipped", true);
        result.put("reason", limit(reason, 500));
        result.put("scopeVersion", project.scopeVersion());
        result.put("submittedAt", LocalDateTime.now(ZoneOffset.UTC).toString());
        repository.skipDiagnosis(userId, projectId, json(result));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.JobAccepted generatePlan(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "PLAN_REQUIRED");
        boolean extensionMode = Boolean.TRUE.equals(project.planCandidate().get("extensionMode"));
        Map<String, Object> input = Map.of(
                "target", project.target(), "scope", project.scope(),
                "diagnosis", project.diagnosis(), "currentPlan", project.plan(),
                "requestedDraft", project.planCandidate(), "extensionMode", extensionMode,
                "versions", Map.of(
                        "target", project.targetVersion(), "scope", project.scopeVersion(),
                        "diagnosis", project.diagnosisVersion()));
        return startJob(userId, project, "PLAN_GENERATION", input, () -> {
            String adjustmentInstruction = extensionMode
                    ? "这是扩展计划：保留 currentPlan 中现有任务及其稳定 id，只在后面追加新的阅读、讲解、练习和复盘任务；返回包含原任务和新增任务的完整计划。"
                    : "如果 currentPlan 已存在，这是调整计划：未改变的任务必须保留原 id；只修改确实需要调整的任务，并返回完整计划。";
            Map<String, Object> generated = parseModel(callModel(
                    "你是学习计划规划助手。只返回 JSON 对象，格式为 {\"tasks\":[{\"id\":\"t1\",\"type\":\"READING|EXPLANATION|EXERCISE|REVIEW\",\"title\":\"任务\",\"conceptIds\":[\"n1\"],\"reason\":\"安排原因\",\"durationMinutes\":30,\"completionCriteria\":\"完成标准\",\"date\":null,\"dependencies\":[]}]}. " +
                            "计划必须混合阅读、讲解、练习和复盘四类任务，并让练习出现在相关讲解之后、复盘出现在练习之后。" + adjustmentInstruction +
                            "根据目标可用时间、确认范围和诊断结果安排任务；不要生成资源文件，不要声称用户已经掌握。\n计划输入：" + json(input),
                    userId));
            ensureList(generated, "tasks", "模型没有返回有效的计划任务。");
            normalizePlanTaskTypes(generated);
            generated.put("inputVersions", input.get("versions"));
            generated.put("generatedAt", LocalDateTime.now(ZoneOffset.UTC).toString());
            validatePlan(generated, project.target());
            ensureJobInputsUnchanged(userId, project, project.stage());
            repository.savePlanCandidate(userId, project.externalId(), json(generated));
            return generated;
        });
    }

    public SmartLearningDtos.ProjectDetail savePlanCandidate(
            long userId, String projectId, Map<String, Object> plan) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "PLAN_REQUIRED");
        validateDraftList(plan, "tasks");
        repository.savePlanCandidate(userId, projectId, json(plan));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail confirmPlan(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "PLAN_REQUIRED");
        if (project.planCandidate().isEmpty()) throw new IllegalArgumentException("请先生成或编辑计划。");
        validatePlan(project.planCandidate(), project.target());
        repository.confirmPlan(userId, projectId);
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail saveResourceConfig(
            long userId, String projectId, Map<String, Object> config) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "RESOURCE_CONFIG_REQUIRED");
        validateDraftSize(config);
        repository.saveResourceConfigDraft(userId, projectId, json(config));
        return detail(userId, projectId);
    }

    public SmartLearningDtos.ProjectDetail confirmResourceConfig(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "RESOURCE_CONFIG_REQUIRED");
        if (project.resourceConfigDraft().isEmpty()) throw new IllegalArgumentException("请先完成资源配置。");
        validateResourceConfig(project.resourceConfigDraft());
        repository.confirmResourceConfig(userId, projectId);
        // Materialize the confirmed plan into executable rows now.  The rows
        // are idempotent and can therefore survive a retry or a page refresh.
        int configuredQuestionCount = number(project.resourceConfigDraft().get("questionCount"));
        List<Map<String, Object>> tasks = new ArrayList<>();
        if (project.plan().get("tasks") instanceof List<?> raw) {
            for (Object value : raw) {
                if (!(value instanceof Map<?, ?> source)) continue;
                Map<String, Object> task = new LinkedHashMap<>();
                source.forEach((key, item) -> task.put(String.valueOf(key), item));
                if ("EXERCISE".equals(planTaskType(task, tasks.size()))) {
                    int count = Math.max(1, Math.min(200, configuredQuestionCount));
                    task.put("questionCount", count);
                    task.put("completionCriteria", "完成 " + count + " 道练习题并提交判卷。");
                }
                tasks.add(task);
            }
        }
        repository.provisionTasks(userId, projectId, project.planVersion(), tasks);
        return detail(userId, projectId);
    }

    public SmartLearningDtos.JobAccepted prepareResources(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        expectStageAtOrAfter(project, "READY");
        List<ResourceRecord> requestedResources = repository.findPendingResources(userId, projectId);
        Map<String, Object> input = Map.of(
                "planVersion", project.planVersion(),
                "resourceConfig", project.resourceConfig(),
                "sources", project.sources(),
                "pendingResources", requestedResources.stream().map(item -> Map.of(
                        "resourceId", item.externalId(),
                        "status", item.status(),
                        "updatedAt", item.updatedAt().toString())).toList());
        return startJob(userId, project, "RESOURCE_PREPARATION", input, () -> {
            List<ResourceRecord> pending = repository.findPendingResources(userId, projectId);
            int total = Math.max(1, pending.size());
            int completed = 0;
            for (ResourceRecord resource : pending) {
                repository.markResourceGenerating(userId, resource.externalId());
                try {
                    TaskRecord task = repository.findTask(userId, projectId, resource.taskExternalId())
                            .orElseThrow(() -> new IllegalStateException("学习任务不存在。"));
                    Map<String, Object> content = buildResource(userId, project, task, resource);
                    repository.markResourceReady(userId, resource.externalId(), json(content));
                } catch (Exception exception) {
                    repository.markResourceFailed(userId, resource.externalId(), userMessage(exception));
                }
                completed++;
                repository.updateJobProgress(currentJobId.get(), completed, total);
            }
            return Map.of("prepared", completed, "total", pending.size());
        });
    }

    public SmartLearningDtos.Workspace workspace(long userId, String projectId) {
        ProjectRecord project = requireReadyProject(userId, projectId);
        if (project.plan().get("tasks") instanceof List<?> raw) {
            List<Map<String, Object>> planTasks = raw.stream().filter(value -> value instanceof Map<?, ?>)
                    .map(value -> (Map<String, Object>) value).toList();
            if (!planTasks.isEmpty()) {
                // This is intentionally idempotent. It also repairs task types
                // for plans confirmed before Phase 2 stored an explicit type.
                repository.provisionTasks(userId, projectId, project.planVersion(), planTasks);
            }
        }
        List<TaskRecord> tasks = repository.findTasks(userId, projectId);
        List<ResourceRecord> resources = repository.findResources(userId, projectId);
        List<WrongItemRecord> wrongItems = repository.findWrongItems(userId, projectId);
        List<SmartLearningDtos.TaskView> taskViews = tasks.stream().map(task -> taskView(userId, projectId, project, task, resources)).toList();
        long completed = tasks.stream().filter(task -> "COMPLETED".equals(task.status())).count();
        int progress = tasks.isEmpty() ? 0 : (int) Math.round(completed * 100d / tasks.size());
        ExecutionRecord active = tasks.stream()
                .map(task -> repository.findActiveExecution(userId, projectId, task.externalId()).orElse(null))
                .filter(Objects::nonNull).findFirst().orElse(null);
        int pendingWrongItems = (int) wrongItems.stream().filter(item -> "TO_REVIEW".equals(item.status())).count();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("target", project.target());
        profile.put("diagnosis", project.diagnosis());
        profile.put("scope", project.scope());
        return new SmartLearningDtos.Workspace(project.externalId(), project.name(), project.stage(), progress,
                (int) completed, tasks.size(), wrongItems.size(), pendingWrongItems, profile,
                taskViews, resources.stream().map(this::resourceView).toList(), active == null ? null : executionView(active), project.updatedAt());
    }

    public List<SmartLearningDtos.WrongItemView> wrongItems(long userId, String projectId) {
        requireProject(userId, projectId);
        return repository.findWrongItems(userId, projectId).stream().map(this::wrongItemView).toList();
    }

    public SmartLearningDtos.WrongItemView reviewWrongItem(
            long userId, String projectId, String wrongItemId,
            SmartLearningDtos.ReviewWrongItemRequest request) {
        requireProject(userId, projectId);
        WrongItemRecord item = repository.findWrongItem(userId, projectId, wrongItemId)
                .orElseThrow(() -> new IllegalArgumentException("错题不存在或无权访问。"));
        String answer = text(request.answer());
        if (answer.isBlank()) throw new IllegalArgumentException("请先填写答案。");
        boolean correct = normalize(answer).equals(normalize(item.correctAnswer()));
        repository.reviewWrongItem(userId, projectId, wrongItemId, answer, correct);
        return wrongItemView(repository.findWrongItem(userId, projectId, wrongItemId).orElseThrow());
    }

    public SmartLearningDtos.TutorThreadView tutorThread(long userId, String projectId, String taskId) {
        ProjectRecord project = requireProject(userId, projectId);
        String normalizedTaskId = blankToNull(taskId);
        if (normalizedTaskId != null) {
            repository.findTask(userId, projectId, normalizedTaskId)
                    .orElseThrow(() -> new IllegalArgumentException("学习任务不存在或无权访问。"));
        }
        String contextKey = normalizedTaskId == null ? "PROJECT" : normalizedTaskId;
        TutorThreadRecord linked = repository.findTutorThread(userId, projectId, contextKey).orElse(null);
        if (linked == null) {
            String title = project.name() + (normalizedTaskId == null ? " · AI 助教" : " · 任务助教");
            var conversation = chatRepository.createConversation(
                    userId, null, title, project.knowledgeBaseExternalId());
            linked = repository.linkTutorThread(
                    userId, projectId, normalizedTaskId, contextKey, conversation.id());
        }
        return new SmartLearningDtos.TutorThreadView(
                linked.externalId(), linked.conversationExternalId(), projectId,
                linked.taskExternalId(), normalizedTaskId == null ? "PROJECT" : "TASK");
    }

    public SmartLearningDtos.TaskView task(long userId, String projectId, String taskId) {
        ProjectRecord project = requireReadyProject(userId, projectId);
        TaskRecord task = repository.findTask(userId, projectId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("学习任务不存在或无权访问。"));
        return taskView(userId, projectId, project, task, repository.findResourcesForTask(userId, projectId, taskId));
    }

    public SmartLearningDtos.ExecutionView startExecution(long userId, String projectId, String taskId) {
        requireReadyProject(userId, projectId);
        TaskRecord task = repository.findTask(userId, projectId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("学习任务不存在或无权访问。"));
        if ("CANCELLED".equals(task.status()) || "SKIPPED".equals(task.status())) {
            throw new IllegalStateException("当前任务不可开始。 ");
        }
        ExecutionRecord execution = repository.findActiveExecution(userId, projectId, taskId).orElse(null);
        if (execution == null) {
            String id = repository.createExecution(userId, projectId, taskId);
            execution = repository.findExecution(userId, id).orElseThrow();
        } else if ("PAUSED".equals(execution.status())) {
            repository.updateExecutionStatus(userId, execution.externalId(), "IN_PROGRESS");
            execution = repository.findExecution(userId, execution.externalId()).orElseThrow();
        }
        return executionView(execution);
    }

    public SmartLearningDtos.ExecutionView updateExecutionStatus(long userId, String executionId, String status) {
        ExecutionRecord execution = repository.findExecution(userId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("学习记录不存在或无权访问。"));
        if (!List.of("PAUSED", "IN_PROGRESS", "COMPLETION_PENDING", "COMPLETED", "SKIPPED").contains(status)) {
            throw new IllegalArgumentException("学习记录状态不正确。 ");
        }
        repository.updateExecutionStatus(userId, executionId, status);
        return repository.findExecution(userId, executionId).map(this::executionView).orElseThrow();
    }

    @Transactional(transactionManager = "v2TransactionManager", rollbackFor = Exception.class)
    public SmartLearningDtos.ExecutionView submitExecution(long userId, String executionId) {
        ExecutionRecord execution = repository.findExecution(userId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("学习记录不存在或无权访问。"));
        ProjectRecord project = requireProject(userId, execution.projectExternalId());
        TaskRecord task = repository.findTask(userId, execution.projectExternalId(), execution.taskExternalId())
                .orElseThrow(() -> new IllegalArgumentException("学习任务不存在或无权访问。"));
        List<ResourceRecord> resources = repository.findResourcesForTask(userId, execution.projectExternalId(), execution.taskExternalId());
        ResourceRecord exercise = resources.stream().filter(item -> "EXERCISE_SET".equals(item.kind()) && "READY".equals(item.status())).findFirst().orElse(null);
        SmartLearningDtos.ExerciseGrade grade = null;
        if ("EXERCISE".equals(task.taskType()) && exercise == null) {
            throw new IllegalStateException("练习题还在准备中，生成完成后才能提交判卷。");
        }
        if (exercise != null) {
            List<Map<String, Object>> items = exerciseItems(exercise, project, task);
            if (items.isEmpty()) throw new IllegalStateException("当前练习题无有效内容，请返回工作台重试资源准备。");
            int configuredCount = configuredExerciseCount(project, task);
            if (configuredCount > 0 && items.size() != configuredCount) {
                throw new IllegalStateException("练习题数量与当前计划不一致，请返回工作台重新准备资源。");
            }
            Map<String, Object> answers = execution.answers();
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                Map<String, Object> item = items.get(index);
                values.add(answerFor(answers, text(item.get("id")), index));
            }
            int answered = (int) values.stream().filter(value -> value != null && !text(value).isBlank()).count();
            if (answered < items.size()) {
                throw new IllegalStateException("还有 " + (items.size() - answered) + " 道题未作答，请完成后再提交判卷。 ");
            }
            int correct = 0;
            List<SmartLearningDtos.ExerciseQuestionGrade> results = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                Map<String, Object> item = items.get(index);
                String id = text(item.get("id"));
                Object value = values.get(index);
                boolean isCorrect = normalize(value).equals(normalize(item.get("answer")));
                if (isCorrect) {
                    correct++;
                    repository.markWrongItemMastered(userId, execution.projectExternalId(), task.externalId(), id);
                } else {
                    repository.upsertWrongItem(
                            userId, execution.projectExternalId(), task.externalId(), execution.externalId(), id,
                            limit(text(item.get("stem")), 4000), limit(text(value), 4000),
                            limit(text(item.get("answer")), 4000), limit(text(item.get("explanation")), 8000),
                            limit(defaultText(text(item.get("knowledgeKey")), task.title()), 240));
                }
                results.add(new SmartLearningDtos.ExerciseQuestionGrade(
                        id, index, true, isCorrect, limit(text(value), 4000),
                        limit(text(item.get("answer")), 4000), limit(text(item.get("explanation")), 8000)));
            }
            double accuracy = items.isEmpty() ? 100 : Math.round(correct * 10000d / items.size()) / 100d;
            grade = new SmartLearningDtos.ExerciseGrade(items.size(), answered, correct, accuracy, results);
            repository.updateExecutionScore(userId, executionId, accuracy);
        } else if (execution.progress() < 100 && "READING".equals(task.taskType())) {
            throw new IllegalStateException("请先完成阅读内容，再提交本次任务。 ");
        } else {
            repository.updateExecutionScore(userId, executionId, 100);
        }
        repository.updateExecutionStatus(userId, executionId, "COMPLETED");
        ExecutionRecord completed = repository.findExecution(userId, executionId).orElseThrow();
        return executionView(completed, grade);
    }

    public SmartLearningDtos.ExecutionView saveExecutionProgress(long userId, String executionId, double progress, int secondsDelta) {
        repository.findExecution(userId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("学习记录不存在或无权访问。"));
        repository.updateExecutionProgress(userId, executionId, progress, secondsDelta);
        return repository.findExecution(userId, executionId).map(this::executionView).orElseThrow();
    }

    public SmartLearningDtos.ExecutionView saveExecutionPosition(long userId, String executionId, Map<String, Object> position) {
        repository.findExecution(userId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("学习记录不存在或无权访问。"));
        repository.saveExecutionPosition(userId, executionId, json(position == null ? Map.of() : position));
        return repository.findExecution(userId, executionId).map(this::executionView).orElseThrow();
    }

    public SmartLearningDtos.ExecutionView saveExecutionAnswers(long userId, String executionId, Map<String, Object> answers) {
        repository.findExecution(userId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("学习记录不存在或无权访问。"));
        repository.saveExecutionAnswers(userId, executionId, json(answers == null ? Map.of() : answers));
        return repository.findExecution(userId, executionId).map(this::executionView).orElseThrow();
    }

    public SmartLearningDtos.ExecutionView heartbeat(long userId, String executionId, long sequence, int secondsDelta) {
        ExecutionRecord execution = repository.findExecution(userId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("学习记录不存在或无权访问。"));
        if (sequence > execution.lastHeartbeatSeq()) repository.heartbeat(userId, executionId, sequence, secondsDelta);
        return repository.findExecution(userId, executionId).map(this::executionView).orElseThrow();
    }

    private SmartLearningDtos.TaskView taskView(long userId, String projectId, ProjectRecord project, TaskRecord task, List<ResourceRecord> allResources) {
        List<ResourceRecord> taskResources = allResources.stream()
                .filter(resource -> resource.taskExternalId().equals(task.externalId())).toList();
        ExecutionRecord execution = repository.findLatestExecution(userId, projectId, task.externalId()).orElse(null);
        List<SmartLearningDtos.ResourceView> resources = taskResources.stream().map(this::resourceView).toList();
        SmartLearningDtos.ExerciseGrade grading = execution != null && "COMPLETED".equals(execution.status())
                ? exerciseGrade(taskResources.stream().filter(item -> "EXERCISE_SET".equals(item.kind()) && "READY".equals(item.status())).findFirst().orElse(null), project, task, execution)
                : null;
        return new SmartLearningDtos.TaskView(task.externalId(), task.title(), task.taskType(), task.description(),
                task.completionCriteria(), task.scheduledDate(), task.durationMinutes(), task.status(), task.sortOrder(),
                task.payload(), resources, execution == null ? null : executionView(execution, grading), task.updatedAt());
    }

    private SmartLearningDtos.ResourceView resourceView(ResourceRecord resource) {
        return new SmartLearningDtos.ResourceView(resource.externalId(), resource.taskExternalId(), resource.kind(),
                resource.title(), resource.status(), resource.content(), resource.errorMessage(), resource.updatedAt());
    }

    private SmartLearningDtos.ExecutionView executionView(ExecutionRecord execution) {
        return executionView(execution, null);
    }

    private SmartLearningDtos.ExecutionView executionView(
            ExecutionRecord execution, SmartLearningDtos.ExerciseGrade grading) {
        return new SmartLearningDtos.ExecutionView(execution.externalId(), execution.projectExternalId(), execution.taskExternalId(),
                execution.status(), execution.progress(), execution.accumulatedSeconds(), execution.position(), execution.answers(),
                execution.score(), execution.lastHeartbeatSeq(), execution.startedAt(), execution.pausedAt(), execution.completedAt(), execution.updatedAt(), grading);
    }

    private String planTaskType(Map<String, Object> task, int order) {
        String explicit = text(task.get("type")).toUpperCase();
        if (Set.of("READING", "EXPLANATION", "EXERCISE", "REVIEW").contains(explicit)) return explicit;
        return switch (Math.floorMod(order, 4)) {
            case 1 -> "EXPLANATION";
            case 2 -> "EXERCISE";
            case 3 -> "REVIEW";
            default -> "READING";
        };
    }

    private List<Map<String, Object>> exerciseItems(
            ResourceRecord resource, ProjectRecord project, TaskRecord task) {
        if (resource == null || !(resource.content().get("items") instanceof List<?> raw)) return List.of();
        int expected = expectedExerciseCount(project, task, raw.size());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : raw) {
            if (!(value instanceof Map<?, ?> source)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            source.forEach((key, itemValue) -> item.put(String.valueOf(key), itemValue));
            String id = text(item.get("id"));
            item.put("id", id.isBlank() ? "q" + (result.size() + 1) : id);
            result.add(item);
            if (result.size() >= expected) break;
        }
        return result;
    }

    private int expectedExerciseCount(ProjectRecord project, TaskRecord task, int available) {
        int configured = configuredExerciseCount(project, task);
        return configured > 0 ? Math.min(configured, available) : available;
    }

    private int configuredExerciseCount(ProjectRecord project, TaskRecord task) {
        int payloadCount = number(task.payload().get("questionCount"));
        if (payloadCount > 0) return payloadCount;
        Matcher matcher = Pattern.compile("(\\d+)\\s*(?:道|题)").matcher(task.completionCriteria());
        if (matcher.find()) return Math.max(1, Integer.parseInt(matcher.group(1)));
        return number(project.resourceConfig().get("questionCount"));
    }

    private Object answerFor(Map<String, Object> answers, String id, int index) {
        Object value = answers.get(id + "__" + index);
        return value == null ? answers.get(id) : value;
    }

    private SmartLearningDtos.ExerciseGrade exerciseGrade(
            ResourceRecord resource, ProjectRecord project, TaskRecord task, ExecutionRecord execution) {
        if (resource == null) return null;
        // A completed execution is the only state in which this method is exposed.
        List<Map<String, Object>> items = exerciseItems(resource, project, task);
        List<SmartLearningDtos.ExerciseQuestionGrade> results = new ArrayList<>();
        int correct = 0;
        int answered = 0;
        for (int index = 0; index < items.size(); index++) {
            Map<String, Object> item = items.get(index);
            String id = text(item.get("id"));
            Object answer = answerFor(execution.answers(), id, index);
            boolean hasAnswer = answer != null && !text(answer).isBlank();
            boolean isCorrect = hasAnswer && normalize(answer).equals(normalize(item.get("answer")));
            if (hasAnswer) answered++;
            if (isCorrect) correct++;
            results.add(new SmartLearningDtos.ExerciseQuestionGrade(id, index, hasAnswer, isCorrect,
                    limit(text(answer), 4000), limit(text(item.get("answer")), 4000),
                    limit(text(item.get("explanation")), 8000)));
        }
        double accuracy = items.isEmpty() ? 100 : Math.round(correct * 10000d / items.size()) / 100d;
        return new SmartLearningDtos.ExerciseGrade(items.size(), answered, correct, accuracy, results);
    }

    private Map<String, Object> buildResource(long userId, ProjectRecord project, TaskRecord task, ResourceRecord resource) {
        List<ChunkRecord> chunks = repository.findSourceChunks(userId, project.sources());
        String source = chunks.stream().limit(8).map(chunk -> "[" + chunk.assetName() + "]\n" + limit(chunk.content(), 1800)).reduce("", (a, b) -> a + "\n" + b);
        if ("EXERCISE_SET".equals(resource.kind())) {
            int configuredCount = number(task.payload().get("questionCount"));
            if (configuredCount <= 0) configuredCount = number(project.resourceConfig().get("questionCount"));
            int questionCount = Math.max(1, Math.min(200, configuredCount));
            String prompt = "为学习任务生成恰好 " + questionCount + " 道单选题。只返回 JSON：{\"items\":[{\"id\":\"q1\",\"stem\":\"题干，可包含 Markdown 代码块\",\"options\":[\"A\",\"B\"],\"answer\":\"A\",\"explanation\":\"解析\",\"knowledgeKey\":\"知识点\"}]}。id 必须从 q1 递增且不能重复；每题必须有唯一题干、至少两个选项，answer 必须是 options 中的一个值；代码必须放在 stem 或 explanation 的 Markdown 代码块中。题目必须基于资料，不要编造。任务：" + task.title() + "\n资料：" + limit(source, 16000);
            Map<String, Object> generated = parseModel(callModel(prompt, userId));
            List<Map<String, Object>> items = normalizeExerciseItems(generated, questionCount);
            return Map.of("items", items, "questionCount", items.size());
        }
        String prompt = "为学习任务生成结构清晰的教学讲义。只返回 JSON：{\"markdown\":\"完整 Markdown\"}。" +
                "讲义必须包含学习目标、核心讲解、示例、易错点和小结。代码必须使用带语言标识且成对闭合的代码围栏；表格必须是合法 Markdown 表格；不要把整篇讲义包进代码围栏；不要原样拼接资料片段；没有资料证据时要明确说明。" +
                "\n任务类型：" + task.taskType() + "\n任务：" + task.title() + "\n说明：" + task.description() +
                "\n完成标准：" + task.completionCriteria() + "\n资料：" + limit(source, 18000);
        Map<String, Object> generated = parseModel(callModel(prompt, userId));
        String markdown = sanitizeLearningMarkdown(text(generated.get("markdown")), task.title());
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("markdown", markdown);
        content.put("citations", chunks.stream().limit(8).map(chunk -> Map.of("assetId", chunk.assetExternalId(), "assetName", chunk.assetName(), "versionId", chunk.versionExternalId(), "chunkId", chunk.chunkExternalId(), "pageFrom", chunk.pageFrom() == null ? 0 : chunk.pageFrom(), "pageTo", chunk.pageTo() == null ? 0 : chunk.pageTo())).toList());
        return content;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeExerciseItems(Map<String, Object> generated, int expected) {
        Object rawItems = generated.get("items");
        if (!(rawItems instanceof List<?> raw) || raw.size() < expected) {
            throw new IllegalStateException("模型生成的练习题数量不足，请重试。");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object value : raw) {
            if (!(value instanceof Map<?, ?> source)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            source.forEach((key, itemValue) -> item.put(String.valueOf(key), itemValue));
            String stem = text(item.get("stem"));
            if (stem.isBlank()) continue;
            List<String> options = new ArrayList<>();
            if (item.get("options") instanceof List<?> rawOptions) {
                for (Object option : rawOptions) if (!text(option).isBlank()) options.add(text(option));
            }
            String answer = text(item.get("answer"));
            if (options.size() < 2 || answer.isBlank() || !options.contains(answer)) continue;
            item.put("id", "q" + (items.size() + 1));
            item.put("stem", limit(stem, 12000));
            item.put("options", options.stream().map(option -> limit(option, 2000)).toList());
            item.put("answer", limit(answer, 2000));
            item.put("explanation", limit(text(item.get("explanation")), 8000));
            item.put("knowledgeKey", limit(text(item.get("knowledgeKey")), 240));
            items.add(item);
            if (items.size() >= expected) break;
        }
        if (items.size() < expected) throw new IllegalStateException("模型生成的练习题无法满足题目格式，请重试。");
        return items;
    }

    private String sanitizeLearningMarkdown(String raw, String title) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (value.isBlank()) throw new IllegalStateException("模型没有返回有效的学习讲义，请重试。");
        if (value.matches("(?s)^```(?:markdown|md)\\s*\\n.*\\n```$")) {
            value = value.replaceFirst("(?s)^```(?:markdown|md)\\s*\\n", "")
                    .replaceFirst("(?s)\\n```$", "").trim();
        }
        long fences = value.lines().filter(line -> line.trim().startsWith("```")).count();
        if (fences % 2 != 0) value += "\n```";
        if (!value.matches("(?s)^\\s*#.*")) value = "# " + title + "\n\n" + value;
        return limit(value, 60_000);
    }

    @SuppressWarnings("unchecked")
    private void normalizePlanTaskTypes(Map<String, Object> plan) {
        if (!(plan.get("tasks") instanceof List<?> tasks)) return;
        int index = 0;
        for (Object raw : tasks) {
            if (!(raw instanceof Map<?, ?> source)) continue;
            Map<String, Object> task = (Map<String, Object>) source;
            String type = text(task.get("type")).toUpperCase();
            if (!Set.of("READING", "EXPLANATION", "EXERCISE", "REVIEW").contains(type)) {
                type = switch (Math.floorMod(index, 4)) {
                    case 1 -> "EXPLANATION";
                    case 2 -> "EXERCISE";
                    case 3 -> "REVIEW";
                    default -> "READING";
                };
            }
            task.put("type", type);
            index++;
        }
    }

    private SmartLearningDtos.WrongItemView wrongItemView(WrongItemRecord item) {
        return new SmartLearningDtos.WrongItemView(
                item.externalId(), item.projectExternalId(), item.taskExternalId(),
                item.questionId(), item.stem(), item.userAnswer(), item.correctAnswer(),
                item.explanation(), item.knowledgeKey(), item.status(), item.updatedAt());
    }

    public SmartLearningDtos.JobView job(long userId, String jobId) {
        JobRecord job = repository.findJob(userId, jobId)
                .orElseThrow(() -> new IllegalArgumentException("学习任务不存在或无权访问。"));
        return jobView(job);
    }

    private SmartLearningDtos.JobAccepted startJob(
            long userId,
            ProjectRecord project,
            String kind,
            Map<String, Object> input,
            JobWork work) {
        String fingerprint = crypto.digest("smart-learning:" + kind, json(input));
        JobRecord reusable = repository.findReusableJob(userId, project.externalId(), kind, fingerprint).orElse(null);
        if (reusable != null) return accepted(reusable);
        String jobId = repository.createJob(userId, project.externalId(), kind, fingerprint);
        executor.submit(() -> runJob(userId, project.externalId(), kind, jobId, work));
        return new SmartLearningDtos.JobAccepted(jobId, project.externalId(), kind, "QUEUED");
    }

    private void runJob(long userId, String projectId, String kind, String jobId, JobWork work) {
        repository.markJobRunning(jobId, LocalDateTime.now(ZoneOffset.UTC));
        currentJobId.set(jobId);
        try {
            Map<String, Object> result = work.run();
            repository.markJobSucceeded(jobId, json(result), LocalDateTime.now(ZoneOffset.UTC));
        } catch (Exception exception) {
            repository.markJobFailed(jobId, exception.getMessage(), LocalDateTime.now(ZoneOffset.UTC));
        } finally {
            currentJobId.remove();
        }
    }

    private String callModel(String prompt, long userId) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你必须遵守输出格式，只返回 JSON，不输出 Markdown 代码围栏。"));
        messages.add(Map.of("role", "user", "content", limit(prompt, 70_000)));
        List<AiChatMessage> aiMessages = messages.stream()
                .map(message -> new AiChatMessage(message.get("role"), message.get("content")))
                .toList();
        return ai.completeText(aiMessages, userId).value();
    }

    private Map<String, Object> parseModel(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("``")) value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalStateException("模型返回格式无法解析，请重试。");
        try {
            return objectMapper.readValue(value.substring(start, end + 1), MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("模型返回格式无法解析，请重试。", exception);
        }
    }

    private Map<String, Object> gradeDiagnosis(Map<String, Object> raw, Object rawAnswers) {
        Map<String, Object> answerById = new LinkedHashMap<>();
        if (rawAnswers instanceof List<?> answers) {
            for (Object item : answers) {
                if (item instanceof Map<?, ?> map && map.get("questionId") != null) {
                    answerById.put(String.valueOf(map.get("questionId")), map.get("answer"));
                }
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int objectiveCount = 0;
        int correctCount = 0;
        Object questionsRaw = raw.get("questions");
        if (questionsRaw instanceof List<?> questions) {
            for (Object value : questions) {
                if (!(value instanceof Map<?, ?> question)) continue;
                String id = text(question.get("id"));
                String type = text(question.get("type"));
                Object answer = answerById.get(id);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("questionId", id);
                item.put("conceptId", text(question.get("conceptId")));
                item.put("answer", answer);
                if (type.contains("short") || type.contains("open")) {
                    item.put("status", "NEEDS_REVIEW");
                    item.put("correct", null);
                } else {
                    objectiveCount++;
                    boolean correct = answer != null && normalize(answer).equals(normalize(question.get("answer")));
                    if (correct) correctCount++;
                    item.put("status", answer == null ? "UNANSWERED" : (correct ? "CORRECT" : "INCORRECT"));
                    item.put("correct", correct);
                }
                items.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skipped", false);
        result.put("items", items);
        result.put("objectiveCount", objectiveCount);
        result.put("correctCount", correctCount);
        result.put("score", objectiveCount == 0 ? null : Math.round(correctCount * 100.0 / objectiveCount));
        return result;
    }

    private SmartLearningDtos.ProjectSummary summary(long userId, ProjectRecord project) {
        List<TaskRecord> tasks = repository.findTasks(userId, project.externalId());
        int totalTaskCount = tasks.size();
        int completedTaskCount = (int) tasks.stream().filter(task -> "COMPLETED".equals(task.status())).count();
        int learningProgress = totalTaskCount == 0
                ? 0
                : (int) Math.round(completedTaskCount * 100d / totalTaskCount);
        return new SmartLearningDtos.ProjectSummary(
                project.externalId(), project.name(), project.icon(), project.iconColor(),
                project.knowledgeBaseExternalId(), project.stage(), nextStep(project.stage()),
                project.targetVersion(), project.sourceVersion(), project.scopeVersion(),
                project.diagnosisVersion(), project.planVersion(), project.resourceConfigVersion(),
                learningProgress, completedTaskCount, totalTaskCount,
                project.pinnedAt(), project.updatedAt());
    }

    private SmartLearningDtos.ProjectDetail toDetail(ProjectRecord p, JobRecord activeJob) {
        return new SmartLearningDtos.ProjectDetail(
                p.externalId(), p.name(), p.icon(), p.iconColor(), p.knowledgeBaseExternalId(),
                p.stage(), nextStep(p.stage()), p.target(), p.targetDraft(), p.sources(), p.sourcesDraft(),
                p.scope(), p.scopeCandidate(), p.diagnosis(), sanitizeDiagnosis(p.diagnosisCandidate()), p.diagnosisAnswersDraft(),
                p.plan(), p.planCandidate(), p.resourceConfig(), p.resourceConfigDraft(),
                Map.of("target", p.targetVersion(), "sources", p.sourceVersion(), "scope", p.scopeVersion(),
                        "diagnosis", p.diagnosisVersion(), "plan", p.planVersion(),
                        "resourceConfig", p.resourceConfigVersion()), activeJob == null ? null : jobView(activeJob),
                p.pinnedAt(), p.updatedAt());
    }

    private SmartLearningDtos.JobView jobView(JobRecord job) {
        Map<String, Object> result = "DIAGNOSIS_GENERATION".equals(job.kind())
                ? sanitizeDiagnosis(job.result()) : job.result();
        return new SmartLearningDtos.JobView(job.externalId(), job.projectExternalId(), job.kind(), job.status(),
                job.progressCurrent(), job.progressTotal(), result, job.errorMessage(), job.startedAt(),
                job.finishedAt(), job.createdAt(), job.updatedAt());
    }

    private Map<String, Object> sanitizeDiagnosis(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Map.of();
        Object copy = objectMapper.convertValue(value, Object.class);
        stripPrivateAnswer(copy);
        if (copy instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, MAP_TYPE);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private void stripPrivateAnswer(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            map.remove("answer");
            map.remove("explanation");
            map.values().forEach(this::stripPrivateAnswer);
        } else if (value instanceof List<?> list) {
            list.forEach(this::stripPrivateAnswer);
        }
    }

    private void validateTarget(Map<String, Object> target) {
        if (target == null || target.isEmpty()) throw new IllegalArgumentException("请先填写学习目标。");
        if (text(target.get("examName")).isBlank()) throw new IllegalArgumentException("请填写考试或学习目标。");
        String date = text(target.get("examDate"));
        if (!date.isBlank()) {
            try {
                if (LocalDate.parse(date).isBefore(LocalDate.now(ZoneOffset.UTC))) {
                    throw new IllegalArgumentException("考试或截止日期不能早于今天。");
                }
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalArgumentException("日期格式不正确。");
            }
        }
        if (number(target.get("weeklyMinutes")) <= 0 || number(target.get("weeklyMinutes")) > 10080) {
            throw new IllegalArgumentException("每周可用学习时间需要大于 0 小时，且不能超过 168 小时。");
        }
    }

    private List<List<ChunkRecord>> partitionScopeBatches(List<ChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        List<List<ChunkRecord>> batches = new ArrayList<>();
        List<ChunkRecord> current = new ArrayList<>();
        int currentChars = 0;
        for (ChunkRecord chunk : chunks) {
            int estimate = Math.min(text(chunk.content()).length(), 1800) + 180;
            if (!current.isEmpty() && currentChars + estimate > 36_000) {
                batches.add(current);
                current = new ArrayList<>();
                currentChars = 0;
            }
            current.add(chunk);
            currentChars += estimate;
        }
        if (!current.isEmpty()) batches.add(current);
        return batches;
    }

    private String formatScopeSource(List<ChunkRecord> chunks, String manual) {
        StringBuilder source = new StringBuilder();
        for (ChunkRecord chunk : chunks) {
            source.append("\n[资料 ").append(chunk.assetExternalId()).append(" / ").append(chunk.assetName());
            if (chunk.headingPath() != null) source.append(" / ").append(chunk.headingPath());
            source.append("]\n").append(limit(chunk.content(), 1800)).append('\n');
        }
        if (!manual.isBlank()) source.append("\n[用户手动范围]\n").append(manual);
        return source.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> prefixScopeNodes(Map<String, Object> batch, int batchNumber) {
        Object rawNodes = batch.get("nodes");
        if (!(rawNodes instanceof List<?> nodes)) return List.of();
        String prefix = "b" + batchNumber + "-";
        Set<String> localIds = new HashSet<>();
        for (Object raw : nodes) {
            if (raw instanceof Map<?, ?> node) localIds.add(text(node.get("id")));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : nodes) {
            if (!(raw instanceof Map<?, ?> rawNode)) continue;
            Map<String, Object> node = new LinkedHashMap<>((Map<String, Object>) rawNode);
            String id = text(node.get("id"));
            String parentId = text(node.get("parentId"));
            node.put("id", prefix + id);
            node.put("parentId", parentId.isBlank() || !localIds.contains(parentId) ? null : prefix + parentId);
            result.add(node);
        }
        return result;
    }

    private void validateScope(Map<String, Object> scope) {
        if (scope == null || !(scope.get("nodes") instanceof List<?> nodes) || nodes.isEmpty()) {
            throw new IllegalArgumentException("学习范围至少需要一个知识点。");
        }
        Set<String> ids = new HashSet<>();
        for (Object raw : nodes) {
            if (!(raw instanceof Map<?, ?> node)) {
                throw new IllegalArgumentException("学习范围中存在无效知识点。");
            }
            String id = text(node.get("id"));
            String title = text(node.get("title"));
            if (id.isBlank() || title.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("每个知识点都需要唯一编号和名称。");
            }
        }
        for (Object raw : nodes) {
            Map<?, ?> node = (Map<?, ?>) raw;
            String parentId = text(node.get("parentId"));
            if (!parentId.isBlank() && !ids.contains(parentId)) {
                throw new IllegalArgumentException("学习范围存在找不到的父级知识点。");
            }
        }
    }

    private void validatePlan(Map<String, Object> plan, Map<String, Object> target) {
        if (plan == null || !(plan.get("tasks") instanceof List<?> tasks) || tasks.isEmpty()) {
            throw new IllegalArgumentException("计划至少需要一个任务。");
        }
        Set<String> ids = new HashSet<>();
        Map<String, Integer> taskCountByDate = new LinkedHashMap<>();
        Map<String, List<String>> dependenciesByTask = new LinkedHashMap<>();
        for (Object raw : tasks) {
            if (!(raw instanceof Map<?, ?> task)) {
                throw new IllegalArgumentException("计划中存在无效任务。");
            }
            String id = text(task.get("id"));
            String title = text(task.get("title"));
            int duration = number(task.get("durationMinutes"));
            String criteria = text(task.get("completionCriteria"));
            if (id.isBlank() || title.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("每个计划任务都需要唯一编号和名称。");
            }
            if (duration < 15 || duration > 180) {
                throw new IllegalArgumentException("单个学习任务时长需要在 15 到 180 分钟之间。");
            }
            if (criteria.isBlank()) {
                throw new IllegalArgumentException("每个学习任务都需要填写完成标准。");
            }
            String date = text(task.get("date"));
            if (!date.isBlank()) {
                try {
                    if (LocalDate.parse(date).isBefore(LocalDate.now(ZoneOffset.UTC))) {
                        throw new IllegalArgumentException("计划任务日期不能早于今天。");
                    }
                } catch (java.time.format.DateTimeParseException exception) {
                    throw new IllegalArgumentException("计划任务日期格式不正确。");
                }
                int count = taskCountByDate.merge(date, 1, Integer::sum);
                if (count > 4) throw new IllegalArgumentException("同一天最多安排 4 个核心学习任务。");
            }
            List<String> dependencies = new ArrayList<>();
            if (task.get("dependencies") instanceof List<?> dependencyList) {
                dependencyList.forEach(dependency -> dependencies.add(text(dependency)));
            }
            dependenciesByTask.put(id, dependencies);
        }
        for (Map.Entry<String, List<String>> entry : dependenciesByTask.entrySet()) {
            for (String dependencyId : entry.getValue()) {
                if (!ids.contains(dependencyId) || dependencyId.equals(entry.getKey())) {
                    throw new IllegalArgumentException("计划中存在无效的任务依赖关系。");
                }
            }
        }
        for (String taskId : ids) {
            if (hasDependencyCycle(taskId, dependenciesByTask, new HashSet<>(), new HashSet<>())) {
                throw new IllegalArgumentException("计划中的任务依赖关系不能形成循环。");
            }
        }
        if (number(target == null ? null : target.get("weeklyMinutes")) <= 0) {
            throw new IllegalArgumentException("计划缺少有效的每周可用时间。");
        }
    }

    private boolean hasDependencyCycle(
            String taskId,
            Map<String, List<String>> dependencies,
            Set<String> visiting,
            Set<String> visited) {
        if (visiting.contains(taskId)) return true;
        if (!visited.add(taskId)) return false;
        visiting.add(taskId);
        for (String dependency : dependencies.getOrDefault(taskId, List.of())) {
            if (hasDependencyCycle(dependency, dependencies, visiting, visited)) return true;
        }
        visiting.remove(taskId);
        return false;
    }

    private void validateDiagnosisCandidate(Map<String, Object> diagnosis) {
        Object rawQuestions = diagnosis == null ? null : diagnosis.get("questions");
        if (!(rawQuestions instanceof List<?> questions) || questions.isEmpty() || questions.size() > 50) {
            throw new IllegalArgumentException("诊断题目数量需要在 1 到 50 题之间。");
        }
        Set<String> ids = new HashSet<>();
        for (Object raw : questions) {
            if (!(raw instanceof Map<?, ?> question)) {
                throw new IllegalArgumentException("诊断题目格式不正确。");
            }
            String id = text(question.get("id"));
            String stem = text(question.get("stem"));
            String type = text(question.get("type"));
            if (id.isBlank() || stem.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("每道诊断题都需要唯一编号和题干。");
            }
            if (!type.equals("single_choice") && !type.equals("short_answer") && !type.equals("open")) {
                throw new IllegalArgumentException("诊断题型不受支持。");
            }
            if (type.equals("single_choice")) {
                Object rawOptions = question.get("options");
                if (!(rawOptions instanceof List<?> options) || options.size() < 2) {
                    throw new IllegalArgumentException("选择题至少需要两个选项。");
                }
            }
            if (text(question.get("conceptId")).isBlank()) {
                throw new IllegalArgumentException("诊断题缺少对应的知识点。");
            }
        }
    }

    private void validateResourceConfig(Map<String, Object> config) {
        if (config == null) throw new IllegalArgumentException("请先完成资源配置。");
        String mode = text(config.get("mode"));
        if (!mode.equals("rolling") && !mode.equals("all")) {
            throw new IllegalArgumentException("资源准备方式不正确。");
        }
        int days = number(config.get("effectiveDays"));
        int questionCount = number(config.get("questionCount"));
        if (days < 1 || days > 14) throw new IllegalArgumentException("滚动准备天数需要在 1 到 14 天之间。");
        if (questionCount < 0 || questionCount > 200) throw new IllegalArgumentException("练习题数量需要在 0 到 200 之间。");
        if (text(config.get("difficulty")).isBlank()) throw new IllegalArgumentException("请填写练习题难度。");
    }

    private Map<String, Object> normalizedTarget(Map<String, Object> target) {
        validateDraftSize(target);
        return target == null ? Map.of() : new LinkedHashMap<>(target);
    }

    // Drafts may be incomplete while typing; full business validation belongs to confirmation.
    private void validateDraftSize(Map<String, Object> draft) {
        if (draft == null || json(draft).length() > 200_000) {
            throw new IllegalArgumentException("草稿内容过多，请精简后再保存。");
        }
    }

    private void validateDraftList(Map<String, Object> draft, String key) {
        validateDraftSize(draft);
        if (!(draft.get(key) instanceof List<?> list) || list.size() > 1000
                || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>))) {
            throw new IllegalArgumentException("草稿列表格式不正确，或项目数量过多。");
        }
    }

    private String checkedIcon(String value) {
        if (value != null && !value.matches("[a-z][a-z0-9-]{0,47}")) {
            throw new IllegalArgumentException("请选择有效的项目图标。");
        }
        return value;
    }

    private String checkedColor(String value) {
        if (value != null && !value.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("请选择有效的项目颜色。");
        }
        return value;
    }

    private Map<String, Object> normalizedSources(Map<String, Object> sources) {
        Map<String, Object> normalized = sources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sources);
        Object assets = normalized.get("assets");
        if (!(assets instanceof List<?>)) normalized.put("assets", List.of());
        return normalized;
    }

    private void validateAssetOwnership(long userId, Map<String, Object> sources) {
        for (String assetId : assetIds(sources)) {
            if (!repository.ownsActiveAsset(userId, assetId)) {
                throw new IllegalArgumentException("所选资料不存在、已删除或无权访问。");
            }
        }
        Object raw = sources.get("assets");
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> map)) continue;
                String assetId = text(map.get("assetId"));
                String versionId = text(map.get("versionId"));
                if (!versionId.isBlank() && !repository.ownsReadyVersion(userId, assetId, versionId)) {
                    throw new IllegalArgumentException("所选资料版本已经不可用，请重新选择后再确认。");
                }
            }
        }
    }

    private List<String> assetIds(Map<String, Object> sources) {
        Object raw = sources == null ? null : sources.get("assets");
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof String id && !id.isBlank()) result.add(id.trim());
            if (value instanceof Map<?, ?> map && map.get("assetId") != null) result.add(text(map.get("assetId")));
        }
        return result.stream().filter(item -> !item.isBlank()).distinct().limit(40).toList();
    }

    private void ensureList(Map<String, Object> value, String field, String message) {
        if (!(value.get(field) instanceof List<?> list) || list.isEmpty()) throw new IllegalStateException(message);
    }

    @SuppressWarnings("unchecked")
    private void filterScopeEvidence(Map<String, Object> scope, List<String> allowedAssetIds) {
        Set<String> allowed = new HashSet<>(allowedAssetIds);
        Object rawNodes = scope.get("nodes");
        if (!(rawNodes instanceof List<?> nodes)) return;
        for (Object rawNode : nodes) {
            if (!(rawNode instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> node = (Map<String, Object>) rawMap;
            Object rawEvidence = node.get("evidence");
            if (!(rawEvidence instanceof List<?> evidence)) continue;
            List<Object> valid = new ArrayList<>();
            for (Object item : evidence) {
                if (item instanceof Map<?, ?> citation && allowed.contains(text(citation.get("assetId")))) valid.add(item);
            }
            node.put("evidence", valid);
            if (valid.isEmpty()) {
                String reason = text(node.get("reason"));
                node.put("reason", reason.isBlank() ? "资料中没有可验证的直接依据，请确认。" : reason + "（缺少可验证资料定位）");
            }
        }
    }

    private void expectStage(ProjectRecord project, String... expected) {
        for (String value : expected) if (Objects.equals(value, project.stage())) return;
        throw new IllegalStateException("当前步骤已发生变化，请刷新后继续。");
    }

    private void expectNotArchived(ProjectRecord project) {
        if ("ARCHIVED".equals(project.stage())) {
            throw new IllegalStateException("已归档的学习项目不能继续编辑，请先恢复。" );
        }
    }

    private void expectStageAtOrAfter(ProjectRecord project, String minimum) {
        expectNotArchived(project);
        List<String> order = List.of(
                "TARGET_REQUIRED", "SOURCES_REQUIRED", "SCOPE_REQUIRED",
                "DIAGNOSTIC_REQUIRED", "PLAN_REQUIRED", "RESOURCE_CONFIG_REQUIRED", "READY");
        int currentIndex = order.indexOf(project.stage());
        int minimumIndex = order.indexOf(minimum);
        if (currentIndex < minimumIndex || minimumIndex < 0) {
            throw new IllegalStateException("请先完成前置步骤，再编辑当前内容。" );
        }
    }

    private void ensureJobInputsUnchanged(long userId, ProjectRecord expected, String expectedStage) {
        ProjectRecord current = repository.findProject(userId, expected.externalId())
                .orElseThrow(() -> new IllegalStateException("学习项目已不存在，请刷新后重试。"));
        boolean sameVersions = current.targetVersion() == expected.targetVersion()
                && current.sourceVersion() == expected.sourceVersion()
                && current.scopeVersion() == expected.scopeVersion()
                && current.diagnosisVersion() == expected.diagnosisVersion();
        if (!Objects.equals(current.stage(), expectedStage) || !sameVersions) {
            throw new IllegalStateException("项目输入已更新，本次分析结果已过期，请重新生成。" );
        }
    }

    private ProjectRecord requireProject(long userId, String projectId) {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("项目不存在。");
        return repository.findProject(userId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在或无权访问。"));
    }

    private ProjectRecord requireReadyProject(long userId, String projectId) {
        ProjectRecord project = requireProject(userId, projectId);
        if (!"READY".equals(project.stage())) {
            throw new IllegalStateException("学习项目还在准备阶段，请先完成准备后再进入工作台。");
        }
        return project;
    }

    private SmartLearningDtos.JobAccepted accepted(JobRecord job) {
        return new SmartLearningDtos.JobAccepted(job.externalId(), job.projectExternalId(), job.kind(), job.status());
    }

    private String nextStep(String stage) {
        return switch (stage) {
            case "TARGET_REQUIRED" -> "填写学习目标";
            case "SOURCES_REQUIRED" -> "选择学习资料";
            case "SCOPE_REQUIRED" -> "确认学习范围";
            case "DIAGNOSTIC_REQUIRED" -> "完成基础诊断";
            case "PLAN_REQUIRED" -> "确认学习计划";
            case "RESOURCE_CONFIG_REQUIRED" -> "配置学习资源";
            case "READY" -> "进入学习工作台";
            default -> "查看学习项目";
        };
    }

    private String json(Object value) {
        return repository.writeJson(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String userMessage(Exception error) {
        String value = error == null ? "" : text(error.getMessage());
        return value.isBlank() ? "资源准备失败，请稍后重试。" : value;
    }

    private String blankToNull(String value) {
        String text = text(value);
        return text.isBlank() ? null : text;
    }

    private String defaultText(String value, String fallback) {
        return text(value).isBlank() ? fallback : text(value);
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (Exception ignored) { return 0; }
    }

    private String normalize(Object value) {
        return text(value).replaceAll("\\s+", "").toLowerCase();
    }

    @FunctionalInterface
    private interface JobWork {
        Map<String, Object> run();
    }
}
