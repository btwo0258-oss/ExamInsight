package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_plan")
public class LearningPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long libraryId;
    private String title;
    private String goal;
    private String profile;
    private String agents;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
