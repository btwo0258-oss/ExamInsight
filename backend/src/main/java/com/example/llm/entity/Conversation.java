package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long kbId;
    private String title;
    private Integer messageCount;
    private Integer totalTokens;
    private Integer isPinned; // 0: 未置顶, 1: 已置顶
    private Long learningProjectId; // 关联的学习项目ID
    private String conversationType; // general: 普通对话, learning-setup: 学习配置, learning-tutor: 学习助教
    private Integer status;// 1: normal, 0: deleted/edited
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}