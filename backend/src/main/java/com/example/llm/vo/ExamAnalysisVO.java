package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExamAnalysisVO {
    private Long id;
    private String title;
    private String examType;
    private String fileNames;
    private String content;
    private String keyPoints;
    private String questionDistribution;
    private String suggestions;
    private Long mindMapId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
