package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_mistake")
public class LearningMistake {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long exerciseId;
    private String status;
    private Integer errorCount;
    private Integer reviewCount;
    private Integer correctStreak;
    private String reviewHistory;
    private LocalDateTime lastWrongAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
