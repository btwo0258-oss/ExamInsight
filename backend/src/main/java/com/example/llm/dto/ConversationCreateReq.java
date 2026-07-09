package com.example.llm.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ConversationCreateReq {
    private Long kbId; // Optional: null means no knowledge base (general chat)
    @NotBlank(message = "对话标题不能为空")
    private String title;
}
