package com.example.llm.auth.security;

import com.example.llm.auth.api.AuthApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
public class PasswordPolicy {
    public static final String HASH_POLICY_KEY = "argon2id-v1-m65536-t3-p1";
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456789012345", "passwordpassword", "qwertyqwertyqwerty",
            "111111111111111", "adminadminadmin", "letmeinletmeinletmein",
            "iloveyouiloveyou", "examinsightexaminsight");

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);
    private final String dummyHash = encoder.encode("not-a-real-user-password-value");

    public String normalizeAndValidate(String rawPassword, String normalizedEmail) {
        String password = Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
        int length = password.codePointCount(0, password.length());
        if (length < 15 || length > 128) {
            throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_POLICY_VIOLATION",
                    "密码长度必须为 15 到 128 个字符。");
        }
        if (password.codePoints().anyMatch(Character::isISOControl)) {
            throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_POLICY_VIOLATION",
                    "密码不能包含控制字符。");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        String emailLocalPart = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        if (COMMON_PASSWORDS.contains(lower)
                || lower.contains("examinsight")
                || (emailLocalPart.length() >= 4 && lower.contains(emailLocalPart))) {
            throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_TOO_WEAK",
                    "该密码过于常见或包含账户相关信息，请更换密码。");
        }
        return password;
    }

    public String encode(String normalizedPassword) {
        return encoder.encode(normalizedPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        String normalized = Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
        try {
            return encoder.matches(normalized, encodedPassword == null ? dummyHash : encodedPassword);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void burnDummyVerification(String rawPassword) {
        matches(rawPassword, dummyHash);
    }
}
