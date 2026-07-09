package com.example.llm.utils;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;

import java.util.HashMap;
import java.util.Map;

public class JwtUtils {

    // 生产环境中应将密钥配置在 application.yml 中
    private static final byte[] KEY = "LLM_QA_SECRET_KEY_123456".getBytes();

    /**
     * 生成 Token
     */
    public static String generateToken(Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        // 签发时间
        payload.put(JWT.ISSUED_AT, System.currentTimeMillis() / 1000);
        // 过期时间 (7天)
        payload.put(JWT.EXPIRES_AT, System.currentTimeMillis() / 1000 + 60 * 60 * 24 * 7);

        return JWTUtil.createToken(payload, KEY);
    }

    /**
     * 验证 Token 并获取 userId
     * @return 如果验证失败或过期，返回 null
     */
    public static Long getUserIdFromToken(String token) {
        try {
            boolean verify = JWTUtil.verify(token, KEY);
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
