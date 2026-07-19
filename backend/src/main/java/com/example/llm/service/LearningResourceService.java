package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningResource;

import java.util.List;

public interface LearningResourceService extends IService<LearningResource> {
    List<LearningResource> getResourcesByPlanId(Long planId);
    List<LearningResource> getResourcesByPlanIdAndGroupType(Long planId, String groupType);
    LearningResource createResource(Long planId, String groupType, String title, String description, String action);
    void updateResource(Long resourceId, String title, String description, String action);
    void deleteResource(Long resourceId);
}
