package com.example.llm.vo;

import lombok.Data;

@Data
public class DocStatusVO {
    private Long id;
    private Integer status;
    private Integer chunkCount;
    private String errorMsg;
}