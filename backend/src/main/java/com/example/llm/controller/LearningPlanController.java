package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.LearningPlan;
import com.example.llm.service.LearningPlanService;
import com.example.llm.vo.LearningPlanVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/plans")
public class LearningPlanController {
    
    @Autowired
    private LearningPlanService learningPlanService;
    
    @GetMapping
    public Result<List<LearningPlanVO>> getUserPlans() {
        Long userId = UserContext.getUserId();
        List<LearningPlan> plans = learningPlanService.getUserPlans(userId);
        List<LearningPlanVO> voList = plans.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @GetMapping("/{planId}")
    public Result<LearningPlanVO> getPlanDetail(@PathVariable Long planId) {
        Long userId = UserContext.getUserId();
        LearningPlan plan = learningPlanService.getPlanDetail(userId, planId);
        return Result.success(convertToVO(plan));
    }
    
    @PostMapping
    public Result<LearningPlanVO> createPlan(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long libraryId = Long.valueOf(request.get("libraryId").toString());
        String title = (String) request.get("title");
        String goal = (String) request.get("goal");
        LearningPlan plan = learningPlanService.createPlan(userId, libraryId, title, goal);
        return Result.success(convertToVO(plan));
    }
    
    @PutMapping("/{planId}")
    public Result<Void> updatePlan(@PathVariable Long planId, @RequestBody Map<String, String> request) {
        Long userId = UserContext.getUserId();
        String title = request.get("title");
        String goal = request.get("goal");
        String profile = request.get("profile");
        String agents = request.get("agents");
        learningPlanService.updatePlan(userId, planId, title, goal, profile, agents);
        return Result.success(null);
    }
    
    @DeleteMapping("/{planId}")
    public Result<Void> deletePlan(@PathVariable Long planId) {
        Long userId = UserContext.getUserId();
        learningPlanService.deletePlan(userId, planId);
        return Result.success(null);
    }
    
    private LearningPlanVO convertToVO(LearningPlan plan) {
        LearningPlanVO vo = new LearningPlanVO();
        BeanUtils.copyProperties(plan, vo);
        return vo;
    }
}
