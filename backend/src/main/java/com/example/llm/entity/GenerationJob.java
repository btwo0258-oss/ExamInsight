package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generation_job")
public class GenerationJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobId;
    private Long userId;
    private Long projectId;
    private String type; // profile, plan, adaptive_practice, mistake_review, resource
    private String status; // pending, running, succeeded, failed, cancelled
    private Integer progress; // 0-100
    private String result; // JSON
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
