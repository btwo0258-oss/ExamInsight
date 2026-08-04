package com.example.llm.auth.gateway;

interface HumanVerificationProvider {
    String mode();

    void verify(String token, String remoteAddress);
}
