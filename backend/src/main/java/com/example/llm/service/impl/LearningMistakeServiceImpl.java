package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningMistake;
import com.example.llm.mapper.LearningMistakeMapper;
import com.example.llm.service.LearningMistakeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningMistakeServiceImpl extends ServiceImpl<LearningMistakeMapper, LearningMistake> implements LearningMistakeService {
    
    @Override
    public List<LearningMistake> getMistakesByProjectId(Long projectId) {
        LambdaQueryWrapper<LearningMistake> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningMistake::getProjectId, projectId)
               .orderByDesc(LearningMistake::getUpdateTime);
        return this.list(wrapper);
    }
    
    @Override
    public void recordMistake(Long projectId, Long exerciseId) {
        LambdaQueryWrapper<LearningMistake> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningMistake::getProjectId, projectId)
               .eq(LearningMistake::getExerciseId, exerciseId);
        LearningMistake mistake = this.getOne(wrapper);
        
        if (mistake == null) {
            mistake = new LearningMistake();
            mistake.setProjectId(projectId);
            mistake.setExerciseId(exerciseId);
            mistake.setStatus("needs_review");
            mistake.setErrorCount(1);
            mistake.setReviewCount(0);
            mistake.setCorrectStreak(0);
            mistake.setLastWrongAt(LocalDateTime.now());
            mistake.setCreateTime(LocalDateTime.now());
            mistake.setUpdateTime(LocalDateTime.now());
            this.save(mistake);
        } else {
            mistake.setErrorCount(mistake.getErrorCount() + 1);
            mistake.setLastWrongAt(LocalDateTime.now());
            mistake.setCorrectStreak(0);
            mistake.setUpdateTime(LocalDateTime.now());
            this.updateById(mistake);
        }
    }
    
    @Override
    public void reviewMistake(Long mistakeId, Boolean correct) {
        LearningMistake mistake = this.getById(mistakeId);
        if (mistake == null) {
            throw new IllegalArgumentException("错题记录不存在");
        }
        
        mistake.setReviewCount(mistake.getReviewCount() + 1);
        if (correct) {
            mistake.setCorrectStreak(mistake.getCorrectStreak() + 1);
            if (mistake.getCorrectStreak() >= 3) {
                mistake.setStatus("mastered");
            }
        } else {
            mistake.setCorrectStreak(0);
            mistake.setLastWrongAt(LocalDateTime.now());
        }
        mistake.setUpdateTime(LocalDateTime.now());
        this.updateById(mistake);
    }
}
