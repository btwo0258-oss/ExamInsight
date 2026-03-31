package com.example.llm.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KbUpdateReq {
    private String name;

    @Size(max = 500, message = "知识库描述长度不能超过500")
    private String description;

    private String avatar;

    private String color;
}
