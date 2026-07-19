package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.entity.LearningStage;
import com.example.llm.service.LearningStageService;
import com.example.llm.vo.LearningStageVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/stages")
public class LearningStageController {
    
    @Autowired
    private LearningStageService learningStageService;
    
    @GetMapping("/plan/{planId}")
    public Result<List<LearningStageVO>> getStagesByPlanId(@PathVariable Long planId) {
        List<LearningStage> stages = learningStageService.getStagesByPlanId(planId);
        List<LearningStageVO> voList = stages.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @PostMapping
    public Result<LearningStageVO> createStage(@RequestBody Map<String, Object> request) {
        Long planId = Long.valueOf(request.get("planId").toString());
        Integer stageOrder = Integer.valueOf(request.get("stageOrder").toString());
        String title = (String) request.get("title");
        String duration = (String) request.get("duration");
        String goal = (String) request.get("goal");
        LearningStage stage = learningStageService.createStage(planId, stageOrder, title, duration, goal);
        return Result.success(convertToVO(stage));
    }
    
    @PutMapping("/{stageId}")
    public Result<Void> updateStage(@PathVariable Long stageId, @RequestBody Map<String, String> request) {
        String title = request.get("title");
        String duration = request.get("duration");
        String goal = request.get("goal");
        String status = request.get("status");
        learningStageService.updateStage(stageId, title, duration, goal, status);
        return Result.success(null);
    }
    
    @DeleteMapping("/{stageId}")
    public Result<Void> deleteStage(@PathVariable Long stageId) {
        learningStageService.deleteStage(stageId);
        return Result.success(null);
    }
    
    private LearningStageVO convertToVO(LearningStage stage) {
        LearningStageVO vo = new LearningStageVO();
        BeanUtils.copyProperties(stage, vo);
        return vo;
    }
}
