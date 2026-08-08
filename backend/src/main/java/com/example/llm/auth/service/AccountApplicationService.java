package com.example.llm.auth.service;

import com.example.llm.auth.api.AccountDtos;
import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.domain.AuthModels;
import com.example.llm.auth.repository.AuthRepository;
import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.auth.security.ClientRequestMetadata;
import com.example.llm.auth.security.PasswordPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AccountApplicationService {
    private final AuthRepository repository;
    private final TransactionTemplate transactions;
    private final PasswordPolicy passwordPolicy;
    private final AuthCrypto crypto;
    private final Clock clock;

    public AccountApplicationService(
            AuthRepository repository,
            TransactionTemplate transactions,
            PasswordPolicy passwordPolicy,
            AuthCrypto crypto,
            Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.passwordPolicy = passwordPolicy;
        this.crypto = crypto;
        this.clock = clock;
    }

    public AccountDtos.AccountResponse updateProfile(
            AuthModels.AuthenticatedSession session,
            AccountDtos.UpdateProfileRequest request,
            ClientRequestMetadata metadata) {
        String displayName = normalizeDisplayName(request.displayName());
        LocalDateTime currentTime = now();
        transactions.executeWithoutResult(status -> {
            repository.updateDisplayName(session.userId(), displayName);
            repository.insertSecurityEvent(
                    crypto.newExternalId(), session.userId(), session.sessionId(),
                    "PROFILE_UPDATED", "INFO", null, metadata.ipPrefixHash(), currentTime);
        });
        return new AccountDtos.AccountResponse(
                session.userExternalId(), session.response().email(), displayName);
    }

    public void deleteAccount(
            AuthModels.AuthenticatedSession session,
            AccountDtos.DeleteAccountRequest request,
            ClientRequestMetadata metadata) {
        LocalDateTime currentTime = now();
        LocalDateTime purgeScheduledAt = currentTime.plusSeconds(1);
        transactions.executeWithoutResult(status -> {
            AuthModels.AccountCredential account = repository
                    .findAccountCredentialForUpdate(session.userId())
                    .orElseThrow(this::accountUnavailable);
            if (!List.of("ACTIVE", "LIMITED").contains(account.status())) {
                throw accountUnavailable();
            }
            if (!passwordPolicy.matches(request.currentPassword(), account.passwordHash())) {
                throw new AuthApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CURRENT_PASSWORD",
                        "当前密码错误。");
            }

            repository.insertAccountDeletionRequest(
                    crypto.newExternalId(), account.userId(),
                    crypto.digest("normalized-email", account.normalizedEmail()),
                    account.status(), currentTime, purgeScheduledAt);
            repository.insertSecurityEvent(
                    crypto.newExternalId(), account.userId(), session.sessionId(),
                    "ACCOUNT_DELETION_REQUESTED", "WARN",
                    crypto.digest("normalized-email", account.normalizedEmail()),
                    metadata.ipPrefixHash(), currentTime);
            repository.revokeAllSessions(account.userId(), "ACCOUNT_DELETED", currentTime);
            repository.trashAccount(account.userId(), account.status(), currentTime);
        });
    }

    private String normalizeDisplayName(String rawDisplayName) {
        String value = Normalizer.normalize(rawDisplayName, Normalizer.Form.NFC).trim();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 20 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new AuthApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_DISPLAY_NAME",
                    "昵称需要包含 1 到 20 个字符，且不能包含控制字符。");
        }
        return value;
    }

    private AuthApiException accountUnavailable() {
        return new AuthApiException(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_UNAVAILABLE",
                "该账户当前无法执行此操作。");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

}
