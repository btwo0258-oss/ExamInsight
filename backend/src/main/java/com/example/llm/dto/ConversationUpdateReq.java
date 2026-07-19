package com.example.llm.dto;

import lombok.Data;

@Data
public class ConversationUpdateReq {
    private String title;
    private Boolean isPinned;
    private Long kbId;
    private Long knowledgeBaseId;
    private Long projectId;
    private String conversationType;
}
