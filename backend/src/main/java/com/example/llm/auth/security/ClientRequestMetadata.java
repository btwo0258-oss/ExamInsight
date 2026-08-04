package com.example.llm.auth.security;

public record ClientRequestMetadata(
        String remoteAddress,
        String ipPrefixHash,
        String userAgentHash,
        String deviceHash) {
}
