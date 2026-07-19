package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presentation_job")
public class PresentationJob {
    @TableId(type = IdType.INPUT)
    private String jobId;
    private Long userId;
    private String presentationId;
    private String type;
    private String status;
    private Integer progress;
    private String resultJson;
    private String providerSid;
    private LocalDateTime lastProviderPollAt;
    private String errorCode;
    private String errorMessage;
    private String clientRequestId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
