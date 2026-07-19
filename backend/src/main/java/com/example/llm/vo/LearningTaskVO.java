package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearningTaskVO {
    private Long id;
    private Long stageId;
    private Long projectId;
    private Integer taskOrder;
    private String type;
    private String title;
    private String description;
    private String status;
    private Integer readProgress;
    private Integer validStudySeconds;
    private String completionMode;
    private String exerciseIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
