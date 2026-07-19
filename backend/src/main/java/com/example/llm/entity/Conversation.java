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
    private Integer isPinned; // 是否置顶：0否 1是
    private Long learningProjectId; // 关联的学习项目ID
    private String conversationType; // 对话类型：general-普通对话, learning-setup-学习配置, learning-tutor-学习助教
    private Integer status; // 状态：0正常 1已删除
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}