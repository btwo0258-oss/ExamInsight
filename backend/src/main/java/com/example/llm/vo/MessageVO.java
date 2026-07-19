package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class MessageVO {
    private Long id;
    private Long conversationId;
    private Long parentId;
    private String role; // "user" or "assistant"
    private String content;
    private String sourceChunks; // JSON string of references
    private String model;
    private Integer durationMs;
    private LocalDateTime createTime;
    private String turnId;
    @JsonProperty("qVersion")
    private Integer qVersion;
    @JsonProperty("aVersion")
    private Integer aVersion;
    private String files;
    private String kind;
    private String learningData;
    private String presentationData;
    private String spreadsheetData;
    private String artifacts;
}
