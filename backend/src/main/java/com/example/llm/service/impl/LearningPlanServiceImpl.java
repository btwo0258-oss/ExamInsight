package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningPlan;
import com.example.llm.mapper.LearningPlanMapper;
import com.example.llm.service.LearningPlanService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningPlanServiceImpl extends ServiceImpl<LearningPlanMapper, LearningPlan> implements LearningPlanService {
    
    @Override
    public List<LearningPlan> getUserPlans(Long userId) {
        LambdaQueryWrapper<LearningPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPlan::getUserId, userId)
               .eq(LearningPlan::getStatus, 0)
               .orderByDesc(LearningPlan::getUpdateTime);
        return this.list(wrapper);
    }
    
    @Override
    public LearningPlan getPlanDetail(Long userId, Long planId) {
        LambdaQueryWrapper<LearningPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPlan::getId, planId)
               .eq(LearningPlan::getUserId, userId);
        LearningPlan plan = this.getOne(wrapper);
        if (plan == null) {
            throw new IllegalArgumentException("学习计划不存在或无权限");
        }
        return plan;
    }
    
    @Override
    public LearningPlan createPlan(Long userId, Long libraryId, String title, String goal) {
        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setLibraryId(libraryId);
        plan.setTitle(title);
        plan.setGoal(goal);
        plan.setStatus(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        this.save(plan);
        return plan;
    }
    
    @Override
    public void updatePlan(Long userId, Long planId, String title, String goal, String profile, String agents) {
        LearningPlan plan = getPlanDetail(userId, planId);
        if (title != null) {
            plan.setTitle(title);
        }
        if (goal != null) {
            plan.setGoal(goal);
        }
        if (profile != null) {
            plan.setProfile(profile);
        }
        if (agents != null) {
            plan.setAgents(agents);
        }
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);
    }
    
    @Override
    public void deletePlan(Long userId, Long planId) {
        LearningPlan plan = getPlanDetail(userId, planId);
        plan.setStatus(1);
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);
    }
}
