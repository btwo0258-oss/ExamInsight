package com.example.llm.dto;

import lombok.Data;

@Data
public class UserSettingsUpdateReq {
    private String theme;
    private String defaultModel;
}
