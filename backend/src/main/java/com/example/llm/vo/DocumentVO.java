package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentVO {
    private Long id;
    private Long kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer charCount;
    private Integer chunkCount;
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
