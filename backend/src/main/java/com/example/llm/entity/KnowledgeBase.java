package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_base")
public class KnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String avatar;
    private String color;
    private Integer docCount;
    private Integer chunkCount;
    private Integer mindMapCount;
    private Long examAnalysisId;

    @TableLogic(value = "0", delval = "1")  // 0=未删除, 1=已删除
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}