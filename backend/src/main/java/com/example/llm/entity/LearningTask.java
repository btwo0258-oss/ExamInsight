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
    private String type; // 讲解, 资料, 练习, 测验, 案例
    private String title;
    private String description;
    private String status; // not_started, in_progress, completed
    private Integer readProgress; // 0-100
    private Integer validStudySeconds;
    private String completionMode; // content, resource, exercise, assessment, case
    private String exerciseIds; // JSON array of exercise IDs
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
