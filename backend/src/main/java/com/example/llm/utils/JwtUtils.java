package com.example.llm.utils;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    private final byte[] key;

    public JwtUtils(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 必须配置且长度不能少于 32 个字符");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成 Token
     */
    public String generateToken(Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        // 签发时间
        payload.put(JWT.ISSUED_AT, System.currentTimeMillis() / 1000);
        // 过期时间 (7天)
        payload.put(JWT.EXPIRES_AT, System.currentTimeMillis() / 1000 + 60 * 60 * 24 * 7);

        return JWTUtil.createToken(payload, key);
    }

    /**
     * 验证 Token 并获取 userId
     * @return 如果验证失败或过期，返回 null
     */
    public Long getUserIdFromToken(String token) {
        try {
            boolean verify = JWTUtil.verify(token, key);
            if (!verify) {
                return null;
            }
            JWT jwt = JWTUtil.parseToken(token);
            // 验证是否过期
            Object expObj = jwt.getPayload(JWT.EXPIRES_AT);
            if (expObj != null) {
                long exp = Long.parseLong(expObj.toString());
                if (exp < System.currentTimeMillis() / 1000) {
                    return null;
                }
            }
            Object userId = jwt.getPayload("userId");
            if (userId != null) {
                return Long.valueOf(userId.toString());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
