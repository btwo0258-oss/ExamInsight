package com.example.llm;

import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.Test;

public class PasswordGeneratorTest {
    
    @Test
    public void generatePasswordHashes() {
        String[] passwords = {"20260325", "123", "321", "1", "admin", "123456", "xdx", "btwo", "666", "wusuowei", "1788", "20050329"};
        
        for (String pwd : passwords) {
            String hash = BCrypt.hashpw(pwd, BCrypt.gensalt());
            System.out.println("Password: " + pwd + " -> Hash: " + hash + " (length: " + hash.length() + ")");
        }
    }
}
