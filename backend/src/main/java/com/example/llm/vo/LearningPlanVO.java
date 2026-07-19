package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearningPlanVO {
    private Long id;
    private Long projectId;
    private String planName;
    private String planContent;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
