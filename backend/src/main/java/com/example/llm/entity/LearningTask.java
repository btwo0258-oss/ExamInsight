package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_task")
public class LearningTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stageId;
    private Long projectId;
    private Integer taskOrder;
    private String type;
    private String title;
    private String description;
    private String status;
    private Integer readProgress;
    private Integer validStudySeconds;
    private String completionMode;
    private String exerciseIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
