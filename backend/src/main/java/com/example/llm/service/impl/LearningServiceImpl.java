package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.entity.*;
import com.example.llm.mapper.*;
import com.example.llm.service.LearningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LearningServiceImpl implements LearningService {

    @Autowired
    private LearningProjectMapper projectMapper;

    @Autowired
    private LearningStageMapper stageMapper;

    @Autowired
    private LearningTaskMapper taskMapper;

    @Autowired
    private LearningExerciseMapper exerciseMapper;

    @Autowired
    private LearningMistakeMapper mistakeMapper;

    @Autowired
    private LearningResourceMapper resourceMapper;

    @Autowired
    private GenerationJobMapper jobMapper;

    @Autowired
    private LearningActivityMapper activityMapper;

    @Override
    public LearningProject createProject(Long userId, Long libraryId, String title, String goal) {
        LearningProject project = new LearningProject();
        project.setUserId(userId);
        project.setLibraryId(libraryId);
        project.setTitle(title);
        project.setGoal(goal);
        project.setStatus("draft");
        project.setProgress(0);
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.insert(project);
        return project;
    }

    @Override
    public LearningProject getProject(Long userId, Long projectId) {
        LearningProject project = projectMapper.selectById(projectId);
        if (project == null || !project.getUserId().equals(userId)) {
            throw new RuntimeException("项目不存在或无权限访问");
        }
        return project;
    }

    @Override
    public List<LearningProject> getUserProjects(Long userId) {
        LambdaQueryWrapper<LearningProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProject::getUserId, userId)
               .orderByDesc(LearningProject::getCreateTime);
        return projectMapper.selectList(wrapper);
    }

    @Override
    public LearningProject updateProject(Long userId, Long projectId, Map<String, Object> updates) {
        LearningProject project = getProject(userId, projectId);
        if (updates.containsKey("title")) {
            project.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("goal")) {
            project.setGoal((String) updates.get("goal"));
        }
        if (updates.containsKey("status")) {
            project.setStatus((String) updates.get("status"));
        }
        if (updates.containsKey("progress")) {
            project.setProgress((Integer) updates.get("progress"));
        }
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
        return project;
    }

    @Override
    public void deleteProject(Long userId, Long projectId) {
        LearningProject project = getProject(userId, projectId);
        projectMapper.deleteById(project.getId());
    }

    @Override
    public GenerationJob generateProfile(Long userId, Long libraryId, String text, Map<String, Object> currentProfile) {
        String jobId = UUID.randomUUID().toString();
        GenerationJob job = new GenerationJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setType("profile");
        job.setStatus("running");
        job.setProgress(0);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.insert(job);

        // TODO: 异步调用AI生成学习画像
        // 这里简化处理，实际应该异步执行
        job.setStatus("succeeded");
        job.setProgress(100);
        job.setResult("{\"profile\": \"生成的学习画像数据\"}");
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);

        return job;
    }

    @Override
    public GenerationJob generatePlan(Long userId, Long projectId, Map<String, Object> planConfig) {
        LearningProject project = getProject(userId, projectId);
        String jobId = UUID.randomUUID().toString();
        GenerationJob job = new GenerationJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setProjectId(projectId);
        job.setType("plan");
        job.setStatus("running");
        job.setProgress(0);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.insert(job);

        // TODO: 异步调用AI生成学习计划
        job.setStatus("succeeded");
        job.setProgress(100);
        job.setResult("{\"planId\": " + projectId + "}");
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);

        return job;
    }

    @Override
    public List<LearningStage> getProjectStages(Long userId, Long projectId) {
        getProject(userId, projectId); // 验证权限
        LambdaQueryWrapper<LearningStage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningStage::getProjectId, projectId)
               .orderByAsc(LearningStage::getStageOrder);
        return stageMapper.selectList(wrapper);
    }

    @Override
    public LearningStage createStage(Long userId, Long projectId, Map<String, Object> stageData) {
        getProject(userId, projectId); // 验证权限
        LearningStage stage = new LearningStage();
        stage.setProjectId(projectId);
        stage.setStageOrder((Integer) stageData.getOrDefault("stageOrder", 1));
        stage.setTitle((String) stageData.get("title"));
        stage.setDescription((String) stageData.get("description"));
        stage.setStatus("not_started");
        stage.setCreateTime(LocalDateTime.now());
        stage.setUpdateTime(LocalDateTime.now());
        stageMapper.insert(stage);
        return stage;
    }

    @Override
    public LearningTask createTask(Long userId, Long stageId, Map<String, Object> taskData) {
        LearningStage stage = stageMapper.selectById(stageId);
        if (stage == null) {
            throw new RuntimeException("阶段不存在");
        }
        getProject(userId, stage.getProjectId()); // 验证权限

        LearningTask task = new LearningTask();
        task.setStageId(stageId);
        task.setProjectId(stage.getProjectId());
        task.setTaskOrder((Integer) taskData.getOrDefault("taskOrder", 1));
        task.setTitle((String) taskData.get("title"));
        task.setDescription((String) taskData.get("description"));
        task.setType((String) taskData.get("type"));
        task.setStatus("not_started");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    @Override
    public void recordActivity(Long userId, Long projectId, Long taskId, String eventType, Integer progress, Integer secondsDelta) {
        getProject(userId, projectId); // 验证权限

        LearningActivity activity = new LearningActivity();
        activity.setProjectId(projectId);
        activity.setTaskId(taskId);
        activity.setEventType(eventType);
        activity.setProgress(progress);
        activity.setSecondsDelta(secondsDelta);
        activity.setCreateTime(LocalDateTime.now());
        activityMapper.insert(activity);

        // 更新任务进度
        LearningTask task = taskMapper.selectById(taskId);
        if (task != null) {
            if (progress != null) {
                task.setReadProgress(progress);
            }
            if (secondsDelta != null) {
                Integer current = task.getValidStudySeconds() != null ? task.getValidStudySeconds() : 0;
                task.setValidStudySeconds(current + secondsDelta);
            }
            if ("completed".equals(eventType)) {
                task.setStatus("completed");
            } else if ("started".equals(eventType) || "reading".equals(eventType)) {
                task.setStatus("in_progress");
            }
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    @Override
    public LearningExercise getExercise(Long userId, Long exerciseId) {
        LearningExercise exercise = exerciseMapper.selectById(exerciseId);
        if (exercise == null) {
            throw new RuntimeException("题目不存在");
        }
        // 验证权限
        LearningTask task = taskMapper.selectById(exercise.getTaskId());
        if (task != null) {
            getProject(userId, task.getProjectId());
        }
        return exercise;
    }

    @Override
    public Map<String, Object> submitAnswer(Long userId, Long projectId, Long exerciseId, String answer, String language) {
        getProject(userId, projectId); // 验证权限
        LearningExercise exercise = getExercise(userId, exerciseId);

        // TODO: 实际应该调用判题服务
        boolean correct = answer.equals(exercise.getAnswer());
        int score = correct ? 100 : 0;

        // 记录错题
        if (!correct) {
            LearningMistake mistake = new LearningMistake();
            mistake.setProjectId(projectId);
            mistake.setExerciseId(exerciseId);
            mistake.setStatus("needs_review");
            mistake.setErrorCount(1);
            mistake.setReviewCount(0);
            mistake.setCorrectStreak(0);
            mistake.setLastWrongAt(LocalDateTime.now());
            mistake.setCreateTime(LocalDateTime.now());
            mistake.setUpdateTime(LocalDateTime.now());
            mistakeMapper.insert(mistake);
        }

        return Map.of(
            "correct", correct,
            "score", score,
            "correctAnswer", exercise.getAnswer(),
            "explanation", exercise.getExplanation()
        );
    }

    @Override
    public List<LearningMistake> getProjectMistakes(Long userId, Long projectId) {
        getProject(userId, projectId); // 验证权限
        LambdaQueryWrapper<LearningMistake> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningMistake::getProjectId, projectId)
               .orderByDesc(LearningMistake::getCreateTime);
        return mistakeMapper.selectList(wrapper);
    }

    @Override
    public GenerationJob generateMistakes(Long userId, Long projectId, Long taskId, Integer count) {
        getProject(userId, projectId); // 验证权限
        String jobId = UUID.randomUUID().toString();
        GenerationJob job = new GenerationJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setProjectId(projectId);
        job.setType("mistakes");
        job.setStatus("running");
        job.setProgress(0);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.insert(job);

        // TODO: 异步生成错题
        job.setStatus("succeeded");
        job.setProgress(100);
        job.setResult("{\"count\": " + count + "}");
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);

        return job;
    }

    @Override
    public List<LearningResource> getProjectResources(Long userId, Long projectId) {
        getProject(userId, projectId); // 验证权限
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningResource::getProjectId, projectId)
               .orderByDesc(LearningResource::getCreateTime);
        return resourceMapper.selectList(wrapper);
    }

    @Override
    public GenerationJob generateResource(Long userId, Long projectId, Long taskId, String resourceType) {
        getProject(userId, projectId); // 验证权限
        String jobId = UUID.randomUUID().toString();
        GenerationJob job = new GenerationJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setProjectId(projectId);
        job.setType("resource");
        job.setStatus("running");
        job.setProgress(0);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.insert(job);

        // TODO: 异步生成资源
        job.setStatus("succeeded");
        job.setProgress(100);
        job.setResult("{\"resourceType\": \"" + resourceType + "\"}");
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);

        return job;
    }

    @Override
    public GenerationJob getJob(String jobId) {
        LambdaQueryWrapper<GenerationJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationJob::getJobId, jobId);
        return jobMapper.selectOne(wrapper);
    }
}
