package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningMistake;

import java.util.List;

public interface LearningMistakeService extends IService<LearningMistake> {
    List<LearningMistake> getMistakesByProjectId(Long projectId);
    void recordMistake(Long projectId, Long exerciseId);
    void reviewMistake(Long mistakeId, Boolean correct);
}
