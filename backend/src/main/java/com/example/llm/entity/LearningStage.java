package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_stage")
public class LearningStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer stageOrder;
    private String title;
    private String description;
    private String status; // not_started, in_progress, completed
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
