package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversationListVO {
    private Long id;
    private String title;
    private Long kbId;
    private String kbName; // name of the associated knowledge base
    private Integer messageCount;
    private LocalDateTime updateTime;
}
