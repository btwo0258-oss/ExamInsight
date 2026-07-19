package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningTask;
import com.example.llm.mapper.LearningTaskMapper;
import com.example.llm.service.LearningTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningTaskServiceImpl extends ServiceImpl<LearningTaskMapper, LearningTask> implements LearningTaskService {
    
    @Override
    public List<LearningTask> getTasksByStageId(Long stageId) {
        LambdaQueryWrapper<LearningTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningTask::getStageId, stageId)
               .orderByAsc(LearningTask::getTaskOrder);
        return this.list(wrapper);
    }
    
    @Override
    public List<LearningTask> getTasksByProjectId(Long projectId) {
        LambdaQueryWrapper<LearningTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningTask::getProjectId, projectId)
               .orderByAsc(LearningTask::getTaskOrder);
        return this.list(wrapper);
    }
    
    @Override
    public LearningTask createTask(Long stageId, Long projectId, Integer taskOrder, String type, String title, String description) {
        LearningTask task = new LearningTask();
        task.setStageId(stageId);
        task.setProjectId(projectId);
        task.setTaskOrder(taskOrder);
        task.setType(type);
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus("not_started");
        task.setReadProgress(0);
        task.setValidStudySeconds(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        this.save(task);
        return task;
    }
    
    @Override
    public void updateTask(Long taskId, String title, String description, String status, Integer readProgress, Integer validStudySeconds) {
        LearningTask task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("学习任务不存在");
        }
        if (title != null) {
            task.setTitle(title);
        }
        if (description != null) {
            task.setDescription(description);
        }
        if (status != null) {
            task.setStatus(status);
        }
        if (readProgress != null) {
            task.setReadProgress(readProgress);
        }
        if (validStudySeconds != null) {
            task.setValidStudySeconds(validStudySeconds);
        }
        task.setUpdateTime(LocalDateTime.now());
        this.updateById(task);
    }
    
    @Override
    public void deleteTask(Long taskId) {
        LearningTask task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("学习任务不存在");
        }
        this.removeById(taskId);
    }
}
