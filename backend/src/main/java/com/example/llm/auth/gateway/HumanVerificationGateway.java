package com.example.llm.auth.gateway;

public interface HumanVerificationGateway {
    void verify(String token, String remoteAddress);
}
