package com.example.llm.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountDtos {
    private AccountDtos() {
    }

    public record AccountResponse(
            String userId,
            String email,
            String displayName) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String displayName) {
    }

    public record DeleteAccountRequest(
            @NotBlank @Size(max = 512) String currentPassword,
            @NotBlank @Pattern(regexp = "DELETE_MY_ACCOUNT") String confirmation) {
    }
}
