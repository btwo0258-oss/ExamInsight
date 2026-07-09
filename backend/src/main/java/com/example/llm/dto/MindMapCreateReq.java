package com.example.llm.dto;

import lombok.Data;

@Data
public class MindMapCreateReq {
    private String title;
    private Long kbId;
    private String content;
}
