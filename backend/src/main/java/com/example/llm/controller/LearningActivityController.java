package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.entity.LearningActivity;
import com.example.llm.service.LearningActivityService;
import com.example.llm.vo.LearningActivityVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/activities")
public class LearningActivityController {
    
    @Autowired
    private LearningActivityService learningActivityService;
    
    @GetMapping("/project/{projectId}")
    public Result<List<LearningActivityVO>> getActivitiesByProjectId(@PathVariable Long projectId) {
        List<LearningActivity> activities = learningActivityService.getActivitiesByProjectId(projectId);
        List<LearningActivityVO> voList = activities.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @PostMapping
    public Result<Void> recordActivity(@RequestBody Map<String, Object> request) {
        Long projectId = Long.valueOf(request.get("projectId").toString());
        Long taskId = Long.valueOf(request.get("taskId").toString());
        String eventType = (String) request.get("eventType");
        Integer progress = request.get("progress") != null ? Integer.valueOf(request.get("progress").toString()) : null;
        Integer secondsDelta = request.get("secondsDelta") != null ? Integer.valueOf(request.get("secondsDelta").toString()) : null;
        String action = (String) request.get("action");
        String clientRequestId = (String) request.get("clientRequestId");
        learningActivityService.recordActivity(projectId, taskId, eventType, progress, secondsDelta, action, clientRequestId);
        return Result.success(null);
    }
    
    private LearningActivityVO convertToVO(LearningActivity activity) {
        LearningActivityVO vo = new LearningActivityVO();
        BeanUtils.copyProperties(activity, vo);
        return vo;
    }
}
