package com.example.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KbCreateReq {
    @NotBlank(message = "知识库名称不能为空")
    @Size(min = 1, max = 100, message = "知识库名称长度必须在1-100之间")
    private String name;

    @Size(max = 500, message = "知识库描述长度不能超过500")
    private String description;

    private String avatar;

    private String color;
}
