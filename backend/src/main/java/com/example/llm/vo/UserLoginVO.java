package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserLoginVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private LocalDateTime lastLoginTime;
    private String token;
}
