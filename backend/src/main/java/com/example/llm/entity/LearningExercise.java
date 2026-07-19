package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_exercise")
public class LearningExercise {
    @TableId(type = IdType.AUTO)
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
