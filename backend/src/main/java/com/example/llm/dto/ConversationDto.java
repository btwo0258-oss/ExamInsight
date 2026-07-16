package com.example.llm.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话DTO，用于返回会话详情
 */
@Data
public class ConversationDto {
    private Long id;
    private String title;
    private Long kbId;
    private String kbName;
    private Boolean isPinned;
    private Integer messageCount;
    private Integer totalTokens;
    private Long learningProjectId;
    private String learningProjectName;
    private String conversationType;
    private LocalDateTime updateTime;
    private LocalDateTime createTime;
}
