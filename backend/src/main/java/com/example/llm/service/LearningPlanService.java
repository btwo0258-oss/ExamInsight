package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningPlan;

import java.util.List;

public interface LearningPlanService extends IService<LearningPlan> {
    List<LearningPlan> getUserPlans(Long userId);
    LearningPlan getPlanDetail(Long userId, Long planId);
    LearningPlan createPlan(Long userId, Long libraryId, String title, String goal);
    void updatePlan(Long userId, Long planId, String title, String goal, String profile, String agents);
    void deletePlan(Long userId, Long planId);
}
