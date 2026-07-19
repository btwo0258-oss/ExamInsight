package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningResource;
import com.example.llm.mapper.LearningResourceMapper;
import com.example.llm.service.LearningResourceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LearningResourceServiceImpl extends ServiceImpl<LearningResourceMapper, LearningResource> implements LearningResourceService {
    
    @Override
    public List<LearningResource> getResourcesByPlanId(Long planId) {
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningResource::getPlanId, planId);
        return this.list(wrapper);
    }
    
    @Override
    public List<LearningResource> getResourcesByPlanIdAndGroupType(Long planId, String groupType) {
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningResource::getPlanId, planId)
               .eq(LearningResource::getGroupType, groupType);
        return this.list(wrapper);
    }
    
    @Override
    public LearningResource createResource(Long planId, String groupType, String title, String description, String action) {
        LearningResource resource = new LearningResource();
        resource.setPlanId(planId);
        resource.setGroupType(groupType);
        resource.setTitle(title);
        resource.setDescription(description);
        resource.setAction(action);
        this.save(resource);
        return resource;
    }
    
    @Override
    public void updateResource(Long resourceId, String title, String description, String action) {
        LearningResource resource = this.getById(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("学习资源不存在");
        }
        if (title != null) {
            resource.setTitle(title);
        }
        if (description != null) {
            resource.setDescription(description);
        }
        if (action != null) {
            resource.setAction(action);
        }
        this.updateById(resource);
    }
    
    @Override
    public void deleteResource(Long resourceId) {
        LearningResource resource = this.getById(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("学习资源不存在");
        }
        this.removeById(resourceId);
    }
}
