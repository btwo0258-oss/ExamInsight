package com.example.llm.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class LearningPlanCreateReq {
    @NotNull(message = "知识库ID不能为空")
    private Long libraryId;

    @NotBlank(message = "计划标题不能为空")
    private String title;

    @NotBlank(message = "学习目标不能为空")
    private String goal;

    private String profile; // JSON 格式
}
