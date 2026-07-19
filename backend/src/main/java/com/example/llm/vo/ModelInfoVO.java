package com.example.llm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ModelInfoVO {
    private String name;
    private String label;
    private String displayName;
    private String description;
    private Boolean enabled;
    private List<String> capabilities;
}
