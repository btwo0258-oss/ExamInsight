package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.*;
import com.example.llm.service.LearningService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    @Autowired
    private LearningService learningService;

    // ==================== 项目管理 ====================

    @PostMapping("/projects")
    public Result<LearningProject> createProject(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long libraryId = Long.valueOf(request.get("libraryId").toString());
        String title = (String) request.get("title");
        String goal = (String) request.get("goal");
        LearningProject project = learningService.createProject(userId, libraryId, title, goal);
        return Result.success(project);
    }

    @GetMapping("/projects")
    public Result<List<LearningProject>> getUserProjects() {
        Long userId = UserContext.getUserId();
        List<LearningProject> projects = learningService.getUserProjects(userId);
        return Result.success(projects);
    }

    @GetMapping("/projects/{projectId}")
    public Result<LearningProject> getProject(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        LearningProject project = learningService.getProject(userId, projectId);
        return Result.success(project);
    }

    @PutMapping("/projects/{projectId}")
    public Result<LearningProject> updateProject(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> updates) {
        Long userId = UserContext.getUserId();
        LearningProject project = learningService.updateProject(userId, projectId, updates);
        return Result.success(project);
    }

    @DeleteMapping("/projects/{projectId}")
    public Result<Void> deleteProject(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        learningService.deleteProject(userId, projectId);
        return Result.success(null);
    }

    // ==================== 学习画像生成 ====================

    @PostMapping("/profile-jobs")
    public Result<GenerationJob> generateProfile(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long libraryId = Long.valueOf(request.get("libraryId").toString());
        String text = (String) request.get("text");
        @SuppressWarnings("unchecked")
        Map<String, Object> currentProfile = (Map<String, Object>) request.get("currentProfile");
        GenerationJob job = learningService.generateProfile(userId, libraryId, text, currentProfile);
        return Result.success(job);
    }

    // ==================== 学习方案生成 ====================

    @PostMapping("/projects/drafts")
    public Result<LearningProject> createDraft(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long libraryId = Long.valueOf(request.get("libraryId").toString());
        String title = (String) request.get("title");
        String goal = (String) request.get("goal");
        LearningProject project = learningService.createProject(userId, libraryId, title, goal);
        return Result.success(project);
    }

    @PostMapping("/plan-jobs")
    public Result<GenerationJob> generatePlan(@RequestBody Map<String, Object> planConfig) {
        Long userId = UserContext.getUserId();
        Long projectId = Long.valueOf(planConfig.get("projectId").toString());
        GenerationJob job = learningService.generatePlan(userId, projectId, planConfig);
        return Result.success(job);
    }

    @PostMapping("/profile-confirmations")
    public Result<Map<String, String>> generateConfirmation(@RequestBody Map<String, Object> request) {
        // TODO: 实现学习画像确认稿生成
        return Result.success(Map.of("content", "确认稿内容"));
    }

    @GetMapping("/generation-jobs/{jobId}")
    public Result<GenerationJob> getGenerationJob(@PathVariable String jobId) {
        GenerationJob job = learningService.getJob(jobId);
        return Result.success(job);
    }

    // ==================== 阶段和任务管理 ====================

    @GetMapping("/projects/{projectId}/stages")
    public Result<List<LearningStage>> getProjectStages(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        List<LearningStage> stages = learningService.getProjectStages(userId, projectId);
        return Result.success(stages);
    }

    @PostMapping("/projects/{projectId}/stages")
    public Result<LearningStage> createStage(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> stageData) {
        Long userId = UserContext.getUserId();
        LearningStage stage = learningService.createStage(userId, projectId, stageData);
        return Result.success(stage);
    }

    @PostMapping("/stages/{stageId}/tasks")
    public Result<LearningTask> createTask(
            @PathVariable Long stageId,
            @RequestBody Map<String, Object> taskData) {
        Long userId = UserContext.getUserId();
        LearningTask task = learningService.createTask(userId, stageId, taskData);
        return Result.success(task);
    }

    // ==================== 学习行为记录 ====================

    @PostMapping("/projects/{projectId}/activities")
    public Result<Void> recordActivity(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> activityData) {
        Long userId = UserContext.getUserId();
        Long taskId = Long.valueOf(activityData.get("taskId").toString());
        String eventType = (String) activityData.get("eventType");
        Integer progress = activityData.get("progress") != null ? 
            Integer.valueOf(activityData.get("progress").toString()) : null;
        Integer secondsDelta = activityData.get("secondsDelta") != null ? 
            Integer.valueOf(activityData.get("secondsDelta").toString()) : null;
        learningService.recordActivity(userId, projectId, taskId, eventType, progress, secondsDelta);
        return Result.success(null);
    }

    // ==================== 题目管理 ====================

    @GetMapping("/exercises/{exerciseId}")
    public Result<LearningExercise> getExercise(@PathVariable Long exerciseId) {
        Long userId = UserContext.getUserId();
        LearningExercise exercise = learningService.getExercise(userId, exerciseId);
        return Result.success(exercise);
    }

    @PostMapping("/projects/{projectId}/answers")
    public Result<Map<String, Object>> submitAnswer(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> answerData) {
        Long userId = UserContext.getUserId();
        Long exerciseId = Long.valueOf(answerData.get("exerciseId").toString());
        String answer = (String) answerData.get("answer");
        String language = (String) answerData.get("language");
        Map<String, Object> result = learningService.submitAnswer(userId, projectId, exerciseId, answer, language);
        return Result.success(result);
    }

    // ==================== 错题管理 ====================

    @GetMapping("/projects/{projectId}/mistakes")
    public Result<List<LearningMistake>> getProjectMistakes(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        List<LearningMistake> mistakes = learningService.getProjectMistakes(userId, projectId);
        return Result.success(mistakes);
    }

    @PostMapping("/projects/{projectId}/mistake-review-jobs")
    public Result<GenerationJob> generateMistakes(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long taskId = Long.valueOf(request.get("taskId").toString());
        Integer count = Integer.valueOf(request.get("count").toString());
        GenerationJob job = learningService.generateMistakes(userId, projectId, taskId, count);
        return Result.success(job);
    }

    // ==================== 资源管理 ====================

    @GetMapping("/projects/{projectId}/resources")
    public Result<List<LearningResource>> getProjectResources(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        List<LearningResource> resources = learningService.getProjectResources(userId, projectId);
        return Result.success(resources);
    }

    @PostMapping("/projects/{projectId}/resource-jobs")
    public Result<GenerationJob> generateResource(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long resourceId = Long.valueOf(request.get("resourceId").toString());
        // 这里简化处理，实际应该根据 resourceId 生成资源
        GenerationJob job = learningService.generateResource(userId, projectId, resourceId, "resource");
        return Result.success(job);
    }

    @GetMapping("/projects/{projectId}/resources/{resourceId}/download")
    public void downloadResource(
            @PathVariable Long projectId,
            @PathVariable Long resourceId,
            HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        // TODO: 实现资源下载逻辑
        response.setStatus(HttpServletResponse.SC_OK);
    }

    // ==================== 异步任务查询 ====================

    @GetMapping("/jobs/{jobId}")
    public Result<GenerationJob> getJob(@PathVariable String jobId) {
        GenerationJob job = learningService.getJob(jobId);
        return Result.success(job);
    }
}
