package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.dto.KnowledgeBaseDto;
import com.example.llm.dto.LearningPlanCreateReq;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.LearningPlan;
import com.example.llm.entity.LearningResource;
import com.example.llm.entity.LearningStage;
import com.example.llm.mapper.LearningPlanMapper;
import com.example.llm.mapper.LearningResourceMapper;
import com.example.llm.mapper.LearningStageMapper;
import com.example.llm.service.KnowledgeBaseService;
import com.example.llm.service.LearningPlanService;
import com.example.llm.vo.LearningPlanVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LearningPlanServiceImpl implements LearningPlanService {

    @Autowired
    private LearningPlanMapper learningPlanMapper;

    @Autowired
    private LearningStageMapper learningStageMapper;

    @Autowired
    private LearningResourceMapper learningResourceMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public LearningPlanVO createPlan(Long userId, LearningPlanCreateReq req) {
        // 创建学习计划
        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setLibraryId(req.getLibraryId());
        plan.setTitle(req.getTitle());
        plan.setGoal(req.getGoal());
        plan.setProfile(req.getProfile());
        plan.setAgents("[]");
        plan.setStatus(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        learningPlanMapper.insert(plan);

        // 生成默认的学习阶段和资源（这里简化处理，实际应该根据 AI 分析生成）
        generateDefaultStagesAndResources(plan.getId(), req.getLibraryId());

        return getPlanDetail(userId, plan.getId());
    }

    @Override
    public List<LearningPlanVO> getUserPlans(Long userId) {
        LambdaQueryWrapper<LearningPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPlan::getUserId, userId)
               .orderByDesc(LearningPlan::getCreateTime);
        List<LearningPlan> plans = learningPlanMapper.selectList(wrapper);
        return plans.stream()
                .map(plan -> convertToVO(plan, true))
                .collect(Collectors.toList());
    }

    @Override
    public LearningPlanVO getPlanDetail(Long userId, Long planId) {
        LearningPlan plan = learningPlanMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new RuntimeException("学习计划不存在");
        }
        return convertToVO(plan, false);
    }

    @Override
    @Transactional
    public void deletePlan(Long userId, Long planId) {
        LearningPlan plan = learningPlanMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new RuntimeException("学习计划不存在");
        }
        // 删除阶段
        LambdaQueryWrapper<LearningStage> stageWrapper = new LambdaQueryWrapper<>();
        stageWrapper.eq(LearningStage::getProjectId, planId);
        learningStageMapper.delete(stageWrapper);
        // 删除资源
        LambdaQueryWrapper<LearningResource> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.eq(LearningResource::getProjectId, planId);
        learningResourceMapper.delete(resourceWrapper);
        // 删除计划
        learningPlanMapper.deleteById(planId);
    }

    private void generateDefaultStagesAndResources(Long planId, Long libraryId) {
        // 生成默认阶段
        List<LearningStage> stages = new ArrayList<>();
        stages.add(createStage(planId, 1, "基础概念补齐", "2 天", "掌握核心概念", "done"));
        stages.add(createStage(planId, 2, "进阶学习", "4 天", "深入理解知识点", "active"));
        stages.add(createStage(planId, 3, "综合练习", "3 天", "巩固所学内容", "pending"));
        stages.forEach(learningStageMapper::insert);

        // 生成默认资源
        List<LearningResource> resources = new ArrayList<>();
        resources.add(createResource(planId, "文档", "个性化讲义", "根据您的学习情况生成"));
        resources.add(createResource(planId, "结构图", "知识思维导图", "核心知识点可视化"));
        resources.add(createResource(planId, "练习", "分层练习题", "选择题、判断题、代码题"));
        resources.add(createResource(planId, "实操", "代码案例", "实际项目案例"));
        resources.forEach(learningResourceMapper::insert);
    }

    private LearningStage createStage(Long planId, Integer order, String title, String duration, String goal, String status) {
        LearningStage stage = new LearningStage();
        stage.setProjectId(planId);
        stage.setStageOrder(order);
        stage.setTitle(title);
        stage.setDescription(goal); // 将 goal 映射到 description
        stage.setStatus(status);
        return stage;
    }

    private LearningResource createResource(Long planId, String group, String title, String desc) {
        LearningResource resource = new LearningResource();
        resource.setProjectId(planId);
        resource.setGroupType(group);
        resource.setTitle(title);
        resource.setDescription(desc);
        resource.setAction("查看");
        return resource;
    }

    private LearningPlanVO convertToVO(LearningPlan plan, boolean brief) {
        LearningPlanVO vo = new LearningPlanVO();
        vo.setId(plan.getId());
        vo.setLibraryId(plan.getLibraryId());
        vo.setTitle(plan.getTitle());
        vo.setGoal(plan.getGoal());
        vo.setCreateTime(plan.getCreateTime());
        vo.setUpdateTime(plan.getUpdateTime());

        // 获取知识库名称
        try {
            KnowledgeBaseDto kb = knowledgeBaseService.getKnowledgeBaseDetail(plan.getUserId(), plan.getLibraryId());
            vo.setLibraryName(kb.getName());
        } catch (Exception e) {
            vo.setLibraryName("未知知识库");
        }

        // 解析 profile
        try {
            List<LearningPlanVO.ProfileItem> profile = objectMapper.readValue(
                plan.getProfile() != null ? plan.getProfile() : "[]",
                new TypeReference<List<LearningPlanVO.ProfileItem>>() {}
            );
            vo.setProfile(profile);
        } catch (JsonProcessingException e) {
            vo.setProfile(new ArrayList<>());
        }

        // 解析 agents
        try {
            List<LearningPlanVO.AgentVO> agents = objectMapper.readValue(
                plan.getAgents() != null ? plan.getAgents() : "[]",
                new TypeReference<List<LearningPlanVO.AgentVO>>() {}
            );
            vo.setAgents(agents);
        } catch (JsonProcessingException e) {
            vo.setAgents(new ArrayList<>());
        }

        if (!brief) {
            // 获取阶段
            LambdaQueryWrapper<LearningStage> stageWrapper = new LambdaQueryWrapper<>();
            stageWrapper.eq(LearningStage::getProjectId, plan.getId())
                       .orderByAsc(LearningStage::getStageOrder);
            List<LearningStage> stages = learningStageMapper.selectList(stageWrapper);
            vo.setStages(stages.stream().map(this::convertStageToVO).collect(Collectors.toList()));

            // 获取资源
            LambdaQueryWrapper<LearningResource> resourceWrapper = new LambdaQueryWrapper<>();
            resourceWrapper.eq(LearningResource::getProjectId, plan.getId());
            List<LearningResource> resources = learningResourceMapper.selectList(resourceWrapper);
            vo.setResources(resources.stream().map(this::convertResourceToVO).collect(Collectors.toList()));
        }

        return vo;
    }

    private LearningPlanVO.StageVO convertStageToVO(LearningStage stage) {
        LearningPlanVO.StageVO vo = new LearningPlanVO.StageVO();
        vo.setId(stage.getId());
        vo.setStageOrder(stage.getStageOrder());
        vo.setTitle(stage.getTitle());
        vo.setDuration(null);
        vo.setGoal(stage.getDescription());
        vo.setStatus(stage.getStatus());
        vo.setResources(new ArrayList<>());
        return vo;
    }

    private LearningPlanVO.ResourceVO convertResourceToVO(LearningResource resource) {
        LearningPlanVO.ResourceVO vo = new LearningPlanVO.ResourceVO();
        vo.setId(resource.getId());
        vo.setGroup(resource.getGroupType());
        vo.setTitle(resource.getTitle());
        vo.setDesc(resource.getDescription());
        vo.setAction(resource.getAction());
        return vo;
    }
}
