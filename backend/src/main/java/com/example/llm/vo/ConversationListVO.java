package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversationListVO {
    private Long id;
    private String title;
    private Long kbId;
    private String kbName;
    private Integer messageCount;
    private Integer totalTokens;
    private Boolean isPinned;
    private Long learningProjectId;
    private String learningProjectName;
    private String conversationType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
