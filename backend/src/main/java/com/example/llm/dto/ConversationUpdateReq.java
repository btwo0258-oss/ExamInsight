package com.example.llm.dto;

import lombok.Data;

@Data
public class ConversationUpdateReq {
    private String title;
    private Integer isPinned;
    private Long knowledgeBaseId;
    private Long learningProjectId;
    private String conversationType;
}
