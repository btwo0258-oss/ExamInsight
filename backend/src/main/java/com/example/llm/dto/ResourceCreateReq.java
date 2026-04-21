package com.example.llm.dto;

import lombok.Data;

@Data
public class ResourceCreateReq {
    private String title;
    private String category;
    private Integer year;
    private String description;
}
