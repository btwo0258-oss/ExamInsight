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
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String title;
    private String icon;
    private String iconColor;
    private String goal;
    private String targetType;
    private String period;
    private String dailyTime;
    private String weakPoints;
    private String preferences;
    private String payloadJson;
    private String setupStateJson;
    private String activeGenerationJson;
    private String exerciseDraftsJson;
    private String profile;
    private String status;
    private Integer totalTasks;
    private Integer completedTasks;
    private Integer progress;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
