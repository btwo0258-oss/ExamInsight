package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResourceVO {
    private Long id;
    private String title;
    private String category;
    private Integer year;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String description;
    private Integer downloadCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
