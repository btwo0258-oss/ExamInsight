package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearningActivityVO {
    private Long id;
    private Long projectId;
    private Long taskId;
    private String eventType;
    private Integer progress;
    private Integer secondsDelta;
    private String action;
    private String clientRequestId;
    private LocalDateTime createTime;
}
