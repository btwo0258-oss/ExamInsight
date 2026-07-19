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
    private Integer status; // 0: active, 1: deleted/edited

    private String turnId;
    private Integer qVersion;
    private Integer aVersion;
    
    private String files; // JSON string of attached files [{name, type, size}]
    
    private String kind; // "learning-profile" | "learning-document" | "presentation" | "spreadsheet" | null
    private String learningData; // JSON string of learning profile or document data
    private String presentationData; // JSON string of presentation generation data
    private String spreadsheetData; // JSON string of spreadsheet generation data (legacy)
    private String artifacts; // JSON string of ChatArtifactDto[]

    private LocalDateTime createTime;
}
