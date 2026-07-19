package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("learning_stage")
public class LearningStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Integer stageOrder;
    private String title;
    private String duration;
    private String goal;
    private String resources;
    private String status;
}
