package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningStage;

import java.util.List;

public interface LearningStageService extends IService<LearningStage> {
    List<LearningStage> getStagesByPlanId(Long planId);
    LearningStage createStage(Long planId, Integer stageOrder, String title, String duration, String goal);
    void updateStage(Long stageId, String title, String duration, String goal, String status);
    void deleteStage(Long stageId);
}
