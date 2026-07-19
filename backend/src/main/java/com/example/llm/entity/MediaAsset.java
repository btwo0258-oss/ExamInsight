package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_asset")
public class MediaAsset {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String kind;
    private String source;
    private String purpose;
    private String fileName;
    private String mimeType;
    private String filePath;
    private Long size;
    private String status;
    private Long conversationId;
    private Long kbId;
    private Long projectId;
    private String transcript;
    private String clientRequestId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
