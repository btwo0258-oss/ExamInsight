package com.example.llm.dto;

import lombok.Data;

@Data
public class AddToKbReq {
    private Long resourceId;
    private Long kbId;
}
