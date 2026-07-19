package com.example.llm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.LearningProject;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.LearningProjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning/projects")
public class LearningProjectController {

    private final LearningProjectMapper projectMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ObjectMapper objectMapper;

    public LearningProjectController(
            LearningProjectMapper projectMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        Long userId = requireUserId();
        List<LearningProject> projects = projectMapper.selectList(new LambdaQueryWrapper<LearningProject>()
                .eq(LearningProject::getUserId, userId)
                .ne(LearningProject::getStatus, "deleted")
                .orderByDesc(LearningProject::getUpdateTime));
        return Result.success(projects.stream().map(this::toDto).toList());
    }

    @GetMapping("/{projectId}")
    public Result<Map<String, Object>> detail(@PathVariable Long projectId) {
        return Result.success(toDto(requireOwned(projectId)));
    }

    @PostMapping("/drafts")
    public Result<Map<String, Object>> createDraft(@RequestBody Map<String, Object> input) {
        Long userId = requireUserId();
        String title = stringValue(input.get("title"));
        if (title == null || title.isBlank()) throw new IllegalArgumentException("学习项目名称不能为空");
        Long knowledgeBaseId = longValue(input.get("knowledgeBaseId"));
        validateKnowledgeBase(userId, knowledgeBaseId);
        LocalDateTime now = LocalDateTime.now();
        LearningProject project = new LearningProject();
        project.setUserId(userId);
        project.setTitle(title.trim());
        project.setKnowledgeBaseId(knowledgeBaseId);
        project.setKnowledgeBaseName(stringValue(input.get("knowledgeBaseName")));
        project.setIcon(firstNonBlank(stringValue(input.get("icon")), "book"));
        project.setIconColor(firstNonBlank(stringValue(input.get("iconColor")), "#2f6fed"));
        project.setGoal("");
        project.setTargetType("");
        project.setPeriod("");
        project.setDailyTime("");
        project.setStatus("draft");
        project.setTotalTasks(0);
        project.setCompletedTasks(0);
        project.setProgress(0);
        project.setCreateTime(now);
        project.setUpdateTime(now);
        projectMapper.insert(project);
        return Result.success(toDto(project));
    }

    @PostMapping
    public Result<Map<String, Object>> createCompatibility(@RequestBody Map<String, Object> input) {
        if (!input.containsKey("knowledgeBaseId") && input.containsKey("libraryId")) {
            input.put("knowledgeBaseId", input.get("libraryId"));
        }
        return createDraft(input);
    }

    @PatchMapping("/{projectId}")
    public Result<Map<String, Object>> update(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> input) {
        LearningProject project = requireOwned(projectId);
        if (input.containsKey("title")) project.setTitle(requiredText(input, "title"));
        if (input.containsKey("icon")) project.setIcon(stringValue(input.get("icon")));
        if (input.containsKey("iconColor")) project.setIconColor(stringValue(input.get("iconColor")));
        if (input.containsKey("targetType")) {
            project.setTargetType(stringValue(input.get("targetType")));
            project.setGoal(stringValue(input.get("targetType")));
        }
        if (input.containsKey("period")) project.setPeriod(stringValue(input.get("period")));
        if (input.containsKey("dailyTime")) project.setDailyTime(stringValue(input.get("dailyTime")));
        if (input.containsKey("weakPoints")) project.setWeakPoints(stringValue(input.get("weakPoints")));
        if (input.containsKey("preferences")) project.setPreferences(writeJson(input.get("preferences")));
        if (input.containsKey("knowledgeBaseId")) {
            Long kbId = longValue(input.get("knowledgeBaseId"));
            validateKnowledgeBase(project.getUserId(), kbId);
            project.setKnowledgeBaseId(kbId);
        }
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
        return Result.success(toDto(project));
    }

    @PutMapping("/{projectId}")
    public Result<Map<String, Object>> updateCompatibility(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> input) {
        return update(projectId, input);
    }

    @DeleteMapping("/{projectId}")
    public Result<Void> delete(@PathVariable Long projectId) {
        LearningProject project = requireOwned(projectId);
        project.setStatus("deleted");
        project.setSetupStateJson("");
        project.setActiveGenerationJson("");
        project.setExerciseDraftsJson("");
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
        return Result.success(null);
    }

    Map<String, Object> toDto(LearningProject project) {
        Map<String, Object> dto = readMap(project.getPayloadJson());
        dto.put("id", project.getId());
        dto.put("title", project.getTitle());
        dto.put("icon", firstNonBlank(project.getIcon(), "book"));
        dto.put("iconColor", firstNonBlank(project.getIconColor(), "#2f6fed"));
        dto.put("goal", firstNonBlank(project.getGoal(), ""));
        dto.put("updatedAt", project.getUpdateTime() == null ? "" : project.getUpdateTime().toString());
        dto.put("knowledgeBaseId", project.getKnowledgeBaseId());
        dto.put("knowledgeBaseName", project.getKnowledgeBaseName());
        dto.put("status", normalizeStatus(project.getStatus()));
        dto.put("period", firstNonBlank(project.getPeriod(), ""));
        dto.put("targetType", firstNonBlank(project.getTargetType(), ""));
        dto.put("progress", project.getProgress() == null ? 0 : project.getProgress());
        dto.put("taskDone", project.getCompletedTasks() == null ? 0 : project.getCompletedTasks());
        dto.put("totalTasks", project.getTotalTasks() == null ? 0 : project.getTotalTasks());
        putDefault(dto, "exerciseDone", 0);
        putDefault(dto, "totalExercises", 0);
        putDefault(dto, "correctRate", 0);
        putDefault(dto, "weeklyHours", "0h");
        putDefault(dto, "profile", new ArrayList<>());
        putDefault(dto, "stages", new ArrayList<>());
        putDefault(dto, "resources", new ArrayList<>());
        putDefault(dto, "exercises", new ArrayList<>());
        putDefault(dto, "trainingSets", new ArrayList<>());
        putDefault(dto, "wrongQuestions", new ArrayList<>());
        putDefault(dto, "wrongReviewSets", new ArrayList<>());
        putDefault(dto, "dashboard", new ArrayList<>());
        putDefault(dto, "agents", new ArrayList<>());
        return sanitizeExercises(normalizeTaskContracts(dto));
    }

    Map<String, Object> normalizeTaskContracts(Map<String, Object> dto) {
        List<Map<String, Object>> exercises = mapList(dto.get("exercises")).stream()
                .map(LinkedHashMap::new)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        dto.put("exercises", exercises);
        exercises.forEach(this::normalizeExerciseQuestion);
        List<Long> exerciseIds = exercises.stream()
                .map(item -> longValue(item.get("id")))
                .filter(java.util.Objects::nonNull)
                .toList();
        Long readingResourceId = mapList(dto.get("resources")).stream()
                .filter(item -> List.of("学习方案", "个性化学习手册")
                        .contains(stringValue(item.get("group"))))
                .map(item -> longValue(item.get("id")))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        List<Map<String, Object>> exerciseTasks = new ArrayList<>();
        for (Map<String, Object> stage : mapList(dto.get("stages"))) {
            for (Map<String, Object> task : mapList(stage.get("tasks"))) {
                String type = stringValue(task.get("type"));
                task.put("completionMode", completionMode(type));
                if ("资料".equals(type) && longValue(task.get("learningResourceId")) == null
                        && readingResourceId != null) {
                    task.put("learningResourceId", readingResourceId);
                }
                if (List.of("练习", "测验").contains(type)) exerciseTasks.add(task);
            }
        }
        for (int taskIndex = 0; taskIndex < exerciseTasks.size(); taskIndex++) {
            Map<String, Object> task = exerciseTasks.get(taskIndex);
            if (!longList(task.get("exerciseIds")).isEmpty()) continue;
            List<Long> assigned = new ArrayList<>();
            for (int exerciseIndex = 0; exerciseIndex < exerciseIds.size(); exerciseIndex++) {
                if (exerciseIndex % exerciseTasks.size() == taskIndex) {
                    assigned.add(exerciseIds.get(exerciseIndex));
                }
            }
            if (assigned.isEmpty() && !exerciseIds.isEmpty()) {
                assigned.add(exerciseIds.get(taskIndex % exerciseIds.size()));
            }
            task.put("exerciseIds", assigned);
        }
        return dto;
    }

    private void normalizeExerciseQuestion(Map<String, Object> exercise) {
        String title = firstNonBlank(stringValue(exercise.get("title")), "练习题")
                .replaceFirst("(?i)^Ex-?\\d+\\s*", "")
                .trim();
        if (title.length() >= 15 || title.endsWith("？") || title.endsWith("?")) return;
        String type = stringValue(exercise.get("type"));
        String explanation = stringValue(exercise.get("explanation"));
        String answer = stringValue(exercise.get("answer"));
        String question = switch (firstNonBlank(type, "单选题")) {
            case "单选题" -> "关于“" + title + "”，下列说法中正确的是哪一项？";
            case "多选题" -> "关于“" + title + "”，下列说法中正确的有哪些？";
            case "判断题" -> "A".equalsIgnoreCase(firstNonBlank(answer, ""))
                    && explanation != null && explanation.length() >= 8
                    ? "判断下列说法是否正确：" + explanation
                    : "关于“" + title + "”的下列说法是否正确？";
            case "填空题" -> "请写出“" + title + "”对应的完整规律、结论或计算关系。";
            case "简答题" -> "请说明“" + title + "”的解题方法、关键步骤和注意事项。";
            default -> "请完成关于“" + title + "”的这道练习题。";
        };
        exercise.put("title", question);
    }

    private String completionMode(String taskType) {
        return switch (firstNonBlank(taskType, "讲解")) {
            case "资料" -> "resource";
            case "练习" -> "exercise";
            case "测验" -> "assessment";
            case "案例" -> "case";
            default -> "content";
        };
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(this::longValue).filter(java.util.Objects::nonNull).toList();
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

    private Map<String, Object> sanitizeExercises(Map<String, Object> dto) {
        Object exercisesValue = dto.get("exercises");
        if (!(exercisesValue instanceof List<?> exercises)) return dto;
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Object value : exercises) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> exercise = new LinkedHashMap<>();
            raw.forEach((key, item) -> exercise.put(String.valueOf(key), item));
            if (!Boolean.TRUE.equals(exercise.get("submitted"))) {
                exercise.put("answer", "");
                exercise.put("explanation", "");
                exercise.remove("referenceAnswer");
                exercise.remove("requiredCodePatterns");
                exercise.remove("gradingKeywords");
                exercise.remove("gradingRubric");
            }
            sanitized.add(exercise);
        }
        dto.put("exercises", sanitized);
        return dto;
    }

    LearningProject requireOwned(Long projectId) {
        LearningProject project = projectMapper.selectById(projectId);
        if (project == null || !requireUserId().equals(project.getUserId()) || "deleted".equals(project.getStatus())) {
            throw new IllegalArgumentException("学习项目不存在或无权访问");
        }
        return project;
    }

    private void validateKnowledgeBase(Long userId, Long knowledgeBaseId) {
        if (knowledgeBaseId == null) return;
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null || !userId.equals(kb.getUserId()) || !Integer.valueOf(0).equals(kb.getStatus())) {
            throw new IllegalArgumentException("知识库不存在或无权访问");
        }
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        return userId;
    }

    private String requiredText(Map<String, Object> input, String key) {
        String value = stringValue(input.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " 不能为空");
        return value.trim();
    }

    private String normalizeStatus(String status) {
        return switch (firstNonBlank(status, "draft")) {
            case "active" -> "in_progress";
            case "generated" -> "ready";
            default -> status;
        };
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("学习项目数据损坏", error);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("学习项目数据格式不正确", error);
        }
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Long.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void putDefault(Map<String, Object> map, String key, Object value) {
        if (!map.containsKey(key) || map.get(key) == null) map.put(key, value);
    }
}
