package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_activity")
public class LearningActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long taskId;
    private String eventType;
    private Integer progress;
    private Integer secondsDelta;
    private String action;
    private String clientRequestId;
    private LocalDateTime createTime;
}
