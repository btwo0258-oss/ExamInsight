package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presentation")
public class Presentation {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String status;
    private String configJson;
    private String outlineJson;
    private String previewJson;
    private String providerOutlineJson;
    private Long conversationId;
    private String sourceMessageId;
    private Long knowledgeBaseId;
    private Long projectId;
    private Long learningResourceId;
    private String activeJobId;
    private String fileName;
    private Long fileSize;
    private Long documentId;
    private String resourceId;
    private String errorCode;
    private String errorMessage;
    private String clientRequestId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
