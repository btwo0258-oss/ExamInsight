package com.example.llm.auth.security;

import com.example.llm.auth.api.AuthApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.text.Normalizer;
import java.util.Locale;

@Component
public class EmailNormalizer {
    public String normalize(String rawEmail) {
        String email = Normalizer.normalize(rawEmail.trim(), Normalizer.Form.NFC);
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            throw invalidEmail();
        }
        String localPart = email.substring(0, at);
        String domain;
        try {
            domain = IDN.toASCII(email.substring(at + 1), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw invalidEmail();
        }
        String normalized = localPart + "@" + domain;
        if (normalized.length() > 254 || localPart.length() > 64) {
            throw invalidEmail();
        }
        return normalized;
    }

    private AuthApiException invalidEmail() {
        return new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "邮箱地址格式不正确。");
    }
}
