package com.example.llm.service;

import com.example.llm.entity.*;
import java.util.List;
import java.util.Map;

public interface LearningService {
    // 项目管理
    LearningProject createProject(Long userId, Long libraryId, String title, String goal);
    LearningProject getProject(Long userId, Long projectId);
    List<LearningProject> getUserProjects(Long userId);
    LearningProject updateProject(Long userId, Long projectId, Map<String, Object> updates);
    void deleteProject(Long userId, Long projectId);
    
    // 学习画像生成
    GenerationJob generateProfile(Long userId, Long libraryId, String text, Map<String, Object> currentProfile);
    
    // 学习方案生成
    GenerationJob generatePlan(Long userId, Long projectId, Map<String, Object> planConfig);
    
    // 任务管理
    List<LearningStage> getProjectStages(Long userId, Long projectId);
    LearningStage createStage(Long userId, Long projectId, Map<String, Object> stageData);
    LearningTask createTask(Long userId, Long stageId, Map<String, Object> taskData);
    
    // 学习行为记录
    void recordActivity(Long userId, Long projectId, Long taskId, String eventType, Integer progress, Integer secondsDelta);
    
    // 题目管理
    LearningExercise getExercise(Long userId, Long exerciseId);
    Map<String, Object> submitAnswer(Long userId, Long projectId, Long exerciseId, String answer, String language);
    
    // 错题管理
    List<LearningMistake> getProjectMistakes(Long userId, Long projectId);
    GenerationJob generateMistakes(Long userId, Long projectId, Long taskId, Integer count);
    
    // 资源管理
    List<LearningResource> getProjectResources(Long userId, Long projectId);
    GenerationJob generateResource(Long userId, Long projectId, Long taskId, String resourceType);
    
    // 异步任务查询
    GenerationJob getJob(String jobId);
}
