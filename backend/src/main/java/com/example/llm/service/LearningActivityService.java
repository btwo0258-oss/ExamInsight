package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningActivity;

import java.util.List;

public interface LearningActivityService extends IService<LearningActivity> {
    List<LearningActivity> getActivitiesByProjectId(Long projectId);
    void recordActivity(Long projectId, Long taskId, String eventType, Integer progress, 
                       Integer secondsDelta, String action, String clientRequestId);
}
