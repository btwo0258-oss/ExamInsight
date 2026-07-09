package com.example.llm.dto;

import lombok.Data;

@Data
public class ExamAnalysisUpdateReq {
    private Long id;
    private String title;
    private String keyPoints;
    private String questionDistribution;
    private String suggestions;
    private Long mindMapId;
}
