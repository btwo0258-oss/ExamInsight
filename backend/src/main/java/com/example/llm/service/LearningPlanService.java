package com.example.llm.service;

import com.example.llm.dto.LearningPlanCreateReq;
import com.example.llm.vo.LearningPlanVO;

import java.util.List;

public interface LearningPlanService {
    LearningPlanVO createPlan(Long userId, LearningPlanCreateReq req);
    List<LearningPlanVO> getUserPlans(Long userId);
    LearningPlanVO getPlanDetail(Long userId, Long planId);
    void deletePlan(Long userId, Long planId);
}
