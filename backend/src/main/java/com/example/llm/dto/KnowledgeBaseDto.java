package com.example.llm.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库DTO，用于返回给前端
 * 不暴露userId和status字段
 */
@Data
public class KnowledgeBaseDto {
    private Long id;
    private String name;
    private String description;
    private String avatar;
    private String color;
    private Integer docCount;
    private Integer chunkCount;
    private Integer mindMapCount;
    private Long examAnalysisId;
    private Boolean availableForAi; // 是否可用于AI：至少有一个status=1的文档，且至少有一个成功向量化分块
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
