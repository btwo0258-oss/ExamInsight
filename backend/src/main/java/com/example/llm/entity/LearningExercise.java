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
    private String type; // 选择题, 填空题, 判断题, 代码题
    private String title;
    private String content; // 题干
    private String options; // JSON array
    private String answer; // 正确答案
    private String explanation; // 解析
    private String difficulty; // 基础, 进阶, 挑战
    private String cognitiveLevel; // 概念理解, 直接应用, 综合迁移
    private String knowledgePoints; // JSON array
    private String scene; // checkpoint, practice, assessment
    private String purpose; // 随堂检查, 阶段练习, 阶段测验
    private String codeLanguages; // JSON array for code exercises
    private String generationBatch;
    private Integer submitted; // 0 or 1
    private String userAnswer;
    private Integer gradingCorrect; // 0 or 1
    private Integer gradingScore;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
