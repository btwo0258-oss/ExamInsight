package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_job")
public class MediaJob {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String assetId;
    private String mode;
    private String status;
    private Integer progress;
    private String resultJson;
    private String clientRequestId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
