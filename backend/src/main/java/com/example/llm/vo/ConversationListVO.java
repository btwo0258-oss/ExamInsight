package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversationListVO {
    private Long id;
    private String title;
    private Long kbId;
    private String kbName;
    private Boolean isPinned;
    private Integer messageCount;
    private Integer totalTokens;
    private Long learningProjectId;
    private String learningProjectName;
    private String conversationType;
    private LocalDateTime updateTime;
    private LocalDateTime createTime;
}
