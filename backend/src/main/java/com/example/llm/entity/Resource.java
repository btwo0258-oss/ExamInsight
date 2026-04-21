package com.example.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resource")
public class Resource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String category;
    private Integer year;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String description;
    private Integer downloadCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
