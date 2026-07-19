package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearningMistakeVO {
    private Long id;
    private Long userId;
    private Long exerciseId;
    private Long projectId;
    private String wrongAnswer;
    private String correctAnswer;
    private Integer reviewCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
