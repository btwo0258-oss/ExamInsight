package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.entity.LearningExercise;
import com.example.llm.service.LearningExerciseService;
import com.example.llm.vo.LearningExerciseVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/exercises")
public class LearningExerciseController {
    
    @Autowired
    private LearningExerciseService learningExerciseService;
    
    @GetMapping("/task/{taskId}")
    public Result<List<LearningExerciseVO>> getExercisesByTaskId(@PathVariable Long taskId) {
        List<LearningExercise> exercises = learningExerciseService.getExercisesByTaskId(taskId);
        List<LearningExerciseVO> voList = exercises.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @PostMapping
    public Result<LearningExerciseVO> createExercise(@RequestBody Map<String, Object> request) {
        Long projectId = Long.valueOf(request.get("projectId").toString());
        Long taskId = Long.valueOf(request.get("taskId").toString());
        String type = (String) request.get("type");
        String title = (String) request.get("title");
        String content = (String) request.get("content");
        String options = (String) request.get("options");
        String answer = (String) request.get("answer");
        String explanation = (String) request.get("explanation");
        String difficulty = (String) request.get("difficulty");
        LearningExercise exercise = learningExerciseService.createExercise(projectId, taskId, type, title, content, options, answer, explanation, difficulty);
        return Result.success(convertToVO(exercise));
    }
    
    @PostMapping("/{exerciseId}/submit")
    public Result<Void> submitAnswer(@PathVariable Long exerciseId, @RequestBody Map<String, String> request) {
        String userAnswer = request.get("userAnswer");
        learningExerciseService.submitAnswer(exerciseId, userAnswer);
        return Result.success(null);
    }
    
    @PostMapping("/{exerciseId}/grade")
    public Result<Void> gradeExercise(@PathVariable Long exerciseId, @RequestBody Map<String, Object> request) {
        Boolean correct = (Boolean) request.get("correct");
        Integer score = Integer.valueOf(request.get("score").toString());
        learningExerciseService.gradeExercise(exerciseId, correct, score);
        return Result.success(null);
    }
    
    private LearningExerciseVO convertToVO(LearningExercise exercise) {
        LearningExerciseVO vo = new LearningExerciseVO();
        BeanUtils.copyProperties(exercise, vo);
        return vo;
    }
}
