package com.example.llm.dto;

import lombok.Data;

@Data
public class ConversationCreateReq {
    private Long kbId; // Optional: null means no knowledge base (general chat)
    private String title; // Optional, will default to "新对话" if not provided
    private Long learningProjectId;
    private String learningProjectName;
    private String conversationType; // general, learning-setup, learning-tutor
}
