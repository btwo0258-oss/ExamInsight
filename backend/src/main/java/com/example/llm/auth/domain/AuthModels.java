package com.example.llm.auth.domain;

import com.example.llm.auth.api.AuthDtos;

import java.time.LocalDateTime;

public final class AuthModels {
    private AuthModels() {
    }

    public record VerificationChallenge(
            long id,
            String externalId,
            Long userId,
            String normalizedEmail,
            String codeHash,
            String verificationProofHash,
            String status,
            LocalDateTime expiresAt,
            LocalDateTime verifiedAt,
            LocalDateTime proofExpiresAt,
            int attemptCount,
            int maximumAttempts) {
    }

    public record LoginAccount(
            long userId,
            String userExternalId,
            String normalizedEmail,
            String emailDisplay,
            String status,
            String passwordHash,
            String displayName) {
    }

    public record Device(long id, String status) {
    }

    public record SessionRecord(
            long id,
            String externalId,
            long userId,
            String userExternalId,
            String emailDisplay,
            String userStatus,
            String displayName,
            String authLevel,
            String csrfSecretHash,
            LocalDateTime tokenRotatedAt,
            LocalDateTime lastSeenAt,
            LocalDateTime idleExpiresAt,
            LocalDateTime absoluteExpiresAt,
            long rowVersion) {
    }

    public record IssuedSession(
            String sessionToken,
            String csrfToken,
            AuthDtos.SessionResponse response) {
    }

    public record AuthenticatedSession(
            long sessionId,
            String sessionExternalId,
            long userId,
            String userExternalId,
            String csrfSecretHash,
            String rotatedSessionToken,
            String rotatedCsrfToken,
            AuthDtos.SessionResponse response) {
    }
}
