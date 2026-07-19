package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GenerationJobVO {
    private Long id;
    private String jobId;
    private Long userId;
    private Long projectId;
    private String type;
    private String status;
    private Integer progress;
    private String result;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
