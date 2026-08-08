package com.example.llm.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {
    private static final String DEVICE_PATTERN = "[A-Za-z0-9._~-]{16,128}";
    private static final String LEGAL_VERSION_PATTERN = "[A-Za-z0-9._-]{1,32}";

    private AuthDtos() {
    }

    public record RegistrationChallengeRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 4096) String humanVerificationToken,
            @NotBlank @Pattern(regexp = DEVICE_PATTERN) String deviceId) {
    }

    public record RegistrationChallengeResponse(
            String challengeId,
            Instant expiresAt,
            long resendAfterSeconds) {
    }

    public record VerifyEmailRequest(
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record VerificationProofResponse(
            String registrationProof,
            Instant expiresAt) {
    }

    public record PasswordResetChallengeRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 4096) String humanVerificationToken,
            @NotBlank @Pattern(regexp = DEVICE_PATTERN) String deviceId) {
    }

    public record PasswordResetChallengeResponse(
            String challengeId,
            Instant expiresAt,
            long resendAfterSeconds) {
    }

    public record PasswordResetProofResponse(
            String passwordResetToken,
            Instant expiresAt) {
    }

    public record PasswordResetVerifyEmailRequest(
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank @Pattern(regexp = DEVICE_PATTERN) String deviceId) {
    }

    public record PasswordResetRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 16) String newPassword,
            @NotBlank @Size(max = 256) String passwordResetToken,
            @NotBlank @Pattern(regexp = DEVICE_PATTERN) String deviceId) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 16, message = "密码长度必须为 8 到 16 个字符。") String password,
            @NotBlank @Size(max = 256) String registrationProof,
            @NotBlank @Pattern(regexp = DEVICE_PATTERN) String deviceId,
            @NotBlank @Pattern(regexp = LEGAL_VERSION_PATTERN) String termsVersion,
            @NotBlank @Pattern(regexp = LEGAL_VERSION_PATTERN) String privacyVersion,
            @AssertTrue(message = "必须同意用户协议与隐私政策后才能注册。") boolean legalDocumentsAccepted) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 512) String password,
            @NotBlank @Pattern(regexp = DEVICE_PATTERN) String deviceId,
            @Size(max = 4096) String humanVerificationToken) {
    }

    public record SessionResponse(
            String userId,
            String email,
            String displayName,
            String authLevel,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt) {
    }

    public record CsrfResponse(String token) {
    }

    public record ErrorEnvelope(ErrorBody error) {
    }

    public record ErrorBody(
            String code,
            String message,
            String requestId,
            java.util.Map<String, Object> details) {
    }
}
