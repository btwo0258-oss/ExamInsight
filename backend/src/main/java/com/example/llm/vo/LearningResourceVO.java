package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearningResourceVO {
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long resourceId;
    private String resourceType;
    private String title;
    private String description;
    private String url;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
