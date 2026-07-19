package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.entity.LearningTask;
import com.example.llm.service.LearningTaskService;
import com.example.llm.vo.LearningTaskVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/tasks")
public class LearningTaskController {
    
    @Autowired
    private LearningTaskService learningTaskService;
    
    @GetMapping("/stage/{stageId}")
    public Result<List<LearningTaskVO>> getTasksByStageId(@PathVariable Long stageId) {
        List<LearningTask> tasks = learningTaskService.getTasksByStageId(stageId);
        List<LearningTaskVO> voList = tasks.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @GetMapping("/project/{projectId}")
    public Result<List<LearningTaskVO>> getTasksByProjectId(@PathVariable Long projectId) {
        List<LearningTask> tasks = learningTaskService.getTasksByProjectId(projectId);
        List<LearningTaskVO> voList = tasks.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @PostMapping
    public Result<LearningTaskVO> createTask(@RequestBody Map<String, Object> request) {
        Long stageId = Long.valueOf(request.get("stageId").toString());
        Long projectId = Long.valueOf(request.get("projectId").toString());
        Integer taskOrder = Integer.valueOf(request.get("taskOrder").toString());
        String type = (String) request.get("type");
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        LearningTask task = learningTaskService.createTask(stageId, projectId, taskOrder, type, title, description);
        return Result.success(convertToVO(task));
    }
    
    @PutMapping("/{taskId}")
    public Result<Void> updateTask(@PathVariable Long taskId, @RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String status = (String) request.get("status");
        Integer readProgress = request.get("readProgress") != null ? Integer.valueOf(request.get("readProgress").toString()) : null;
        Integer validStudySeconds = request.get("validStudySeconds") != null ? Integer.valueOf(request.get("validStudySeconds").toString()) : null;
        learningTaskService.updateTask(taskId, title, description, status, readProgress, validStudySeconds);
        return Result.success(null);
    }
    
    @DeleteMapping("/{taskId}")
    public Result<Void> deleteTask(@PathVariable Long taskId) {
        learningTaskService.deleteTask(taskId);
        return Result.success(null);
    }
    
    private LearningTaskVO convertToVO(LearningTask task) {
        LearningTaskVO vo = new LearningTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}
