package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.entity.LearningMistake;
import com.example.llm.service.LearningMistakeService;
import com.example.llm.vo.LearningMistakeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/mistakes")
public class LearningMistakeController {
    
    @Autowired
    private LearningMistakeService learningMistakeService;
    
    @GetMapping("/project/{projectId}")
    public Result<List<LearningMistakeVO>> getMistakesByProjectId(@PathVariable Long projectId) {
        List<LearningMistake> mistakes = learningMistakeService.getMistakesByProjectId(projectId);
        List<LearningMistakeVO> voList = mistakes.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @PostMapping
    public Result<Void> recordMistake(@RequestBody Map<String, Long> request) {
        Long projectId = request.get("projectId");
        Long exerciseId = request.get("exerciseId");
        learningMistakeService.recordMistake(projectId, exerciseId);
        return Result.success(null);
    }
    
    @PostMapping("/{mistakeId}/review")
    public Result<Void> reviewMistake(@PathVariable Long mistakeId, @RequestBody Map<String, Boolean> request) {
        Boolean correct = request.get("correct");
        learningMistakeService.reviewMistake(mistakeId, correct);
        return Result.success(null);
    }
    
    private LearningMistakeVO convertToVO(LearningMistake mistake) {
        LearningMistakeVO vo = new LearningMistakeVO();
        BeanUtils.copyProperties(mistake, vo);
        return vo;
    }
}
