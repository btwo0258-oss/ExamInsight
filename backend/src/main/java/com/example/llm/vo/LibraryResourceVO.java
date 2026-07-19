package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LibraryResourceVO {
    private String resourceId;
    private String name;
    private String format;
    private String fileType;
    private String mimeType;
    private Long sizeBytes;
    private String status;
    private String errorMessage;
    private String updatedAt;
    private String sourceType;
    private String origin;
    private Long projectId;
    private Long knowledgeBaseId;
    private String externalKey;
}
