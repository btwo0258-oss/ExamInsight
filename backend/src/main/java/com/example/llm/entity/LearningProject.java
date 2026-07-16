package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_project")
public class LearningProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long libraryId;
    private String title;
    private String goal;
    private String profile; // JSON: 学习画像
    private String status; // draft, configuring, ready, in_progress, completed
    private Integer totalTasks;
    private Integer completedTasks;
    private Integer progress; // 0-100
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
