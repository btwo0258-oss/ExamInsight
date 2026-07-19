package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningTask;

import java.util.List;

public interface LearningTaskService extends IService<LearningTask> {
    List<LearningTask> getTasksByStageId(Long stageId);
    List<LearningTask> getTasksByProjectId(Long projectId);
    LearningTask createTask(Long stageId, Long projectId, Integer taskOrder, String type, String title, String description);
    void updateTask(Long taskId, String title, String description, String status, Integer readProgress, Integer validStudySeconds);
    void deleteTask(Long taskId);
}
