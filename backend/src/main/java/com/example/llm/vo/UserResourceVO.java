package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResourceVO {
    private Long id;
    private Long resourceId;
    private Long kbId;
    private LocalDateTime createTime;
}
