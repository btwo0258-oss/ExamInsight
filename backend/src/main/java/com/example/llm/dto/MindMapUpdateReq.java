package com.example.llm.dto;

import lombok.Data;

@Data
public class MindMapUpdateReq {
    private Long id;
    private String title;
    private Long kbId;
    private String content;
}
