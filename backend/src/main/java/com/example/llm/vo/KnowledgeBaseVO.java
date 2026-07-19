package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeBaseVO {
    private Long id;
    private String name;
    private String description;
    private String avatar;
    private String color;
    private Integer docCount;
    private Integer chunkCount;
    private Integer mindMapCount;
    private List<String> knowledgePoints;
    private Long examAnalysisId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
