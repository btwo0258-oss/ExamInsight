package com.example.llm.dto;

import lombok.Data;
import java.util.List;

/**
 * 模型信息DTO
 */
@Data
public class ModelInfoDto {
    private String name;
    private String label;
    private String displayName;
    private String description;
    private Boolean enabled;
    private List<String> capabilities; // chat, reasoning, vision
}
