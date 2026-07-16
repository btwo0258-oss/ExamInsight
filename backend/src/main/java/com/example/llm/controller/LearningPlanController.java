package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.LearningPlanCreateReq;
import com.example.llm.service.LearningPlanService;
import com.example.llm.vo.LearningPlanVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning-plan")
public class LearningPlanController {

    @Autowired
    private LearningPlanService learningPlanService;

    @PostMapping("/create")
    public Result<LearningPlanVO> create(@Validated @RequestBody LearningPlanCreateReq req) {
        Long userId = UserContext.getUserId();
        LearningPlanVO plan = learningPlanService.createPlan(userId, req);
        return Result.success("创建成功", plan);
    }

    @GetMapping("/list")
    public Result<List<LearningPlanVO>> list() {
        Long userId = UserContext.getUserId();
        List<LearningPlanVO> plans = learningPlanService.getUserPlans(userId);
        return Result.success(plans);
    }

    @GetMapping("/{id}")
    public Result<LearningPlanVO> detail(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        LearningPlanVO plan = learningPlanService.getPlanDetail(userId, id);
        return Result.success(plan);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        learningPlanService.deletePlan(userId, id);
        return Result.success("删除成功", null);
    }
}
