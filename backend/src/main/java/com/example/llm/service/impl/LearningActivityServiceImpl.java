package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningActivity;
import com.example.llm.mapper.LearningActivityMapper;
import com.example.llm.service.LearningActivityService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningActivityServiceImpl extends ServiceImpl<LearningActivityMapper, LearningActivity> implements LearningActivityService {
    
    @Override
    public List<LearningActivity> getActivitiesByProjectId(Long projectId) {
        LambdaQueryWrapper<LearningActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningActivity::getProjectId, projectId)
               .orderByDesc(LearningActivity::getCreateTime);
        return this.list(wrapper);
    }
    
    @Override
    public void recordActivity(Long projectId, Long taskId, String eventType, Integer progress,
                              Integer secondsDelta, String action, String clientRequestId) {
        LearningActivity activity = new LearningActivity();
        activity.setProjectId(projectId);
        activity.setTaskId(taskId);
        activity.setEventType(eventType);
        activity.setProgress(progress);
        activity.setSecondsDelta(secondsDelta);
        activity.setAction(action);
        activity.setClientRequestId(clientRequestId);
        activity.setCreateTime(LocalDateTime.now());
        this.save(activity);
    }
}
