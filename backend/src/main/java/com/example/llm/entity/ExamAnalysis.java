package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_analysis")
public class ExamAnalysis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String examType;
    private String fileNames;
    private String content;
    private String keyPoints;
    private String questionDistribution;
    private String suggestions;
    private Long mindMapId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
