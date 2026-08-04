package com.example.llm.auth.gateway;

import java.time.Instant;

public interface EmailGateway {
    String sendRegistrationCode(String recipient, String code, Instant expiresAt);
}
