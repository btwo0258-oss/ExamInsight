package com.example.llm.auth.gateway;

import java.time.Instant;

public interface EmailGateway {
    enum VerificationPurpose {
        REGISTRATION,
        PASSWORD_RESET
    }

    String sendVerificationCode(
            String recipient,
            String code,
            Instant expiresAt,
            VerificationPurpose purpose);
}
