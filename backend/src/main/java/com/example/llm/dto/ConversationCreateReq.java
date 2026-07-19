package com.example.llm.dto;

import lombok.Data;

@Data
public class ConversationCreateReq {
    private Long kbId;
    private String title;
    private Long projectId;
    private String conversationType;
}
