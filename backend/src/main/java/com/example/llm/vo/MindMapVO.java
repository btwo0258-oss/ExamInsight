package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MindMapVO {
    private Long id;
    private Long kbId;
    private String title;
    private String content;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
