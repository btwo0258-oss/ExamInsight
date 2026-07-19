package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningExercise;
import com.example.llm.mapper.LearningExerciseMapper;
import com.example.llm.service.LearningExerciseService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningExerciseServiceImpl extends ServiceImpl<LearningExerciseMapper, LearningExercise> implements LearningExerciseService {
    
    @Override
    public List<LearningExercise> getExercisesByTaskId(Long taskId) {
        LambdaQueryWrapper<LearningExercise> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningExercise::getTaskId, taskId);
        return this.list(wrapper);
    }
    
    @Override
    public LearningExercise createExercise(Long projectId, Long taskId, String type, String title, String content,
                                           String options, String answer, String explanation, String difficulty) {
        LearningExercise exercise = new LearningExercise();
        exercise.setProjectId(projectId);
        exercise.setTaskId(taskId);
        exercise.setType(type);
        exercise.setTitle(title);
        exercise.setContent(content);
        exercise.setOptions(options);
        exercise.setAnswer(answer);
        exercise.setExplanation(explanation);
        exercise.setDifficulty(difficulty);
        exercise.setSubmitted(0);
        exercise.setCreateTime(LocalDateTime.now());
        exercise.setUpdateTime(LocalDateTime.now());
        this.save(exercise);
        return exercise;
    }
    
    @Override
    public void submitAnswer(Long exerciseId, String userAnswer) {
        LearningExercise exercise = this.getById(exerciseId);
        if (exercise == null) {
            throw new IllegalArgumentException("练习题不存在");
        }
        exercise.setUserAnswer(userAnswer);
        exercise.setSubmitted(1);
        exercise.setUpdateTime(LocalDateTime.now());
        this.updateById(exercise);
    }
    
    @Override
    public void gradeExercise(Long exerciseId, Boolean correct, Integer score) {
        LearningExercise exercise = this.getById(exerciseId);
        if (exercise == null) {
            throw new IllegalArgumentException("练习题不存在");
        }
        exercise.setGradingCorrect(correct ? 1 : 0);
        exercise.setGradingScore(score);
        exercise.setUpdateTime(LocalDateTime.now());
        this.updateById(exercise);
    }
}
