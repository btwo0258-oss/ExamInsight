package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningStage;
import com.example.llm.mapper.LearningStageMapper;
import com.example.llm.service.LearningStageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LearningStageServiceImpl extends ServiceImpl<LearningStageMapper, LearningStage> implements LearningStageService {
    
    @Override
    public List<LearningStage> getStagesByPlanId(Long planId) {
        LambdaQueryWrapper<LearningStage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningStage::getPlanId, planId)
               .orderByAsc(LearningStage::getStageOrder);
        return this.list(wrapper);
    }
    
    @Override
    public LearningStage createStage(Long planId, Integer stageOrder, String title, String duration, String goal) {
        LearningStage stage = new LearningStage();
        stage.setPlanId(planId);
        stage.setStageOrder(stageOrder);
        stage.setTitle(title);
        stage.setDuration(duration);
        stage.setGoal(goal);
        stage.setStatus("pending");
        this.save(stage);
        return stage;
    }
    
    @Override
    public void updateStage(Long stageId, String title, String duration, String goal, String status) {
        LearningStage stage = this.getById(stageId);
        if (stage == null) {
            throw new IllegalArgumentException("学习阶段不存在");
        }
        if (title != null) {
            stage.setTitle(title);
        }
        if (duration != null) {
            stage.setDuration(duration);
        }
        if (goal != null) {
            stage.setGoal(goal);
        }
        if (status != null) {
            stage.setStatus(status);
        }
        this.updateById(stage);
    }
    
    @Override
    public void deleteStage(Long stageId) {
        LearningStage stage = this.getById(stageId);
        if (stage == null) {
            throw new IllegalArgumentException("学习阶段不存在");
        }
        this.removeById(stageId);
    }
}
