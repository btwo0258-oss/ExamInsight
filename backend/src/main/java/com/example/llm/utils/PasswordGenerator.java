package com.example.llm.utils;

import cn.hutool.crypto.digest.BCrypt;

public class PasswordGenerator {
    public static void main(String[] args) {
        String password = "admin123";
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
        System.out.println("Password: " + password);
        System.out.println("Hash: " + hashed);
        System.out.println("Length: " + hashed.length());
        
        // 验证
        boolean matches = BCrypt.checkpw(password, hashed);
        System.out.println("Matches: " + matches);
    }
}
