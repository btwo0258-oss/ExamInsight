package com.example.llm.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ConversationUpdateReq {
    @NotBlank(message = "对话标题不能为空")
    private String title;
}
