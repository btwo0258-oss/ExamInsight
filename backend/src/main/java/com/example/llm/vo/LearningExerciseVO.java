package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearningExerciseVO {
    private Long id;
    private Long projectId;
    private Long taskId;
    private String type;
    private String title;
    private String content;
    private String options;
    private String answer;
    private String explanation;
    private String difficulty;
    private String cognitiveLevel;
    private String knowledgePoints;
    private String scene;
    private String purpose;
    private String codeLanguages;
    private String generationBatch;
    private Integer submitted;
    private String userAnswer;
    private Integer gradingCorrect;
    private Integer gradingScore;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
