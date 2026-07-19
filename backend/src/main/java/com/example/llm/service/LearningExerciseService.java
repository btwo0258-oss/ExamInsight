package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningExercise;

import java.util.List;

public interface LearningExerciseService extends IService<LearningExercise> {
    List<LearningExercise> getExercisesByTaskId(Long taskId);
    LearningExercise createExercise(Long projectId, Long taskId, String type, String title, String content, 
                                    String options, String answer, String explanation, String difficulty);
    void submitAnswer(Long exerciseId, String userAnswer);
    void gradeExercise(Long exerciseId, Boolean correct, Integer score);
}
