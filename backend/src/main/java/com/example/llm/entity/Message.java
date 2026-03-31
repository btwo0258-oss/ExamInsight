package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long parentId; // The ID of the parent message in the conversation tree
    private String role; // "user" or "assistant"
    private String content;
    private Integer tokenCount;
    private String sourceChunks; // JSON string of reference sources
    private String model;
    private Integer durationMs; // response time in ms
    private Integer status; // 1: normal, 0: deleted/edited
    
    private String turnId;
    private Integer qVersion;
    private Integer aVersion;
    
    private String files; // JSON string of attached files [{name, type, size}]
    
    private LocalDateTime createTime;
}
