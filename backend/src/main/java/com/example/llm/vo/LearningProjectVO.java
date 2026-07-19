package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearningProjectVO {
    private Long id;
    private Long libraryId;
    private String title;
    private String goal;
    private String profile;
    private String status;
    private Integer totalTasks;
    private Integer completedTasks;
    private Integer progress;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
