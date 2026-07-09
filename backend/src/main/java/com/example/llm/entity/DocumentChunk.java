package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long kbId;
    private Integer chunkIndex;
    private String content;
    private Integer charCount;
    private Integer tokenCount;
    private Integer embeddingStatus;
    private String esId;
    private String metadata;
    private LocalDateTime createTime;
}