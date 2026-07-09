package com.example.llm.dto;

import lombok.Data;

@Data
public class ExamAnalysisCreateReq {
    private String title;
    private String examType;
    private String fileNames;
}
