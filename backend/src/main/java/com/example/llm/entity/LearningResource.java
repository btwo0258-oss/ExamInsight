package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_resource")
public class LearningResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long stageId;
    private Long taskId;
    private String groupType; // 文档, 结构图, 练习, 实操
    private String title;
    private String description;
    private String status; // not_selected, generating, ready, failed
    private String errorMessage;
    private String fileName;
    private String filePath;
    private String content; // For markdown or text content
    private String action; // 查看, 下载
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
