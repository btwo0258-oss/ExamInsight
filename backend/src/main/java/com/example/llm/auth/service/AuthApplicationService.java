package com.example.llm.auth.service;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.api.AuthDtos;
import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.domain.AuthModels;
import com.example.llm.auth.gateway.EmailGateway;
import com.example.llm.auth.gateway.HumanVerificationGateway;
import com.example.llm.auth.repository.AuthRepository;
import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.auth.security.AuthRateLimiter;
import com.example.llm.auth.security.ClientRequestMetadata;
import com.example.llm.auth.security.EmailNormalizer;
import com.example.llm.auth.security.PasswordPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthApplicationService {
    private static final String REGISTRATION = "REGISTRATION";
    private static final String PASSWORD_RESET = "PASSWORD_RESET";

    private final AuthRepository repository;
    private final TransactionTemplate transactions;
    private final AuthProperties properties;
    private final AuthCrypto crypto;
    private final PasswordPolicy passwordPolicy;
    private final EmailNormalizer emailNormalizer;
    private final AuthRateLimiter rateLimiter;
    private final HumanVerificationGateway humanVerification;
    private final EmailGateway emailGateway;
    private final Clock clock;

    public AuthApplicationService(
            AuthRepository repository,
            @Qualifier("v2TransactionTemplate") TransactionTemplate transactions,
            AuthProperties properties,
            AuthCrypto crypto,
            PasswordPolicy passwordPolicy,
            EmailNormalizer emailNormalizer,
            AuthRateLimiter rateLimiter,
            HumanVerificationGateway humanVerification,
            EmailGateway emailGateway,
            Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.properties = properties;
        this.crypto = crypto;
        this.passwordPolicy = passwordPolicy;
        this.emailNormalizer = emailNormalizer;
        this.rateLimiter = rateLimiter;
        this.humanVerification = humanVerification;
        this.emailGateway = emailGateway;
        this.clock = clock;
    }

    public AuthDtos.RegistrationChallengeResponse createRegistrationChallenge(
            AuthDtos.RegistrationChallengeRequest request,
            ClientRequestMetadata metadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        String emailHash = crypto.digest("normalized-email", normalizedEmail);

        humanVerification.verify(request.humanVerificationToken(), metadata.remoteAddress());
        rateLimiter.consumeRegistration(emailHash, metadata.deviceHash(), metadata.ipPrefixHash());

        String challengeExternalId = crypto.newExternalId();
        String deliveryExternalId = crypto.newExternalId();
        String code = crypto.newVerificationCode();
        String codeHash = crypto.digest("verification-code:" + challengeExternalId, code);
        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plus(properties.getVerification().getCodeTtl());

        ChallengeCreation creation;
        try {
            creation = transactions.execute(status -> {
                if (repository.userExists(normalizedEmail)) {
                    throw new AuthApiException(HttpStatus.CONFLICT, "EMAIL_UNAVAILABLE",
                            "该邮箱无法用于新账户注册。");
                }
                repository.supersedeActiveChallenges(normalizedEmail, REGISTRATION);
                long challengeId = repository.insertVerificationChallenge(
                        challengeExternalId,
                        null,
                        normalizedEmail,
                        REGISTRATION,
                        codeHash,
                        now,
                        expiresAt,
                        properties.getVerification().getMaximumAttempts(),
                        metadata.ipPrefixHash(),
                        metadata.deviceHash());
                long deliveryId = repository.insertQueuedDelivery(
                        deliveryExternalId, challengeId, null, "REGISTRATION_CODE", emailHash);
                return new ChallengeCreation(challengeId, deliveryId);
            });
        } catch (DuplicateKeyException exception) {
            throw new AuthApiException(HttpStatus.CONFLICT, "CHALLENGE_CONFLICT",
                    "验证码请求正在处理中，请稍后重试。");
        }

        if (creation == null) {
            throw new IllegalStateException("注册挑战事务没有返回结果");
        }

        try {
            String providerMessageId = emailGateway.sendVerificationCode(
                    request.email().trim(), code, toInstant(expiresAt),
                    EmailGateway.VerificationPurpose.REGISTRATION);
            LocalDateTime sentAt = now();
            transactions.executeWithoutResult(status ->
                    repository.markDeliverySent(creation.deliveryId(), providerMessageId, sentAt));
        } catch (AuthApiException exception) {
            LocalDateTime failedAt = now();
            transactions.executeWithoutResult(status -> repository.markDeliveryFailedAndSupersedeChallenge(
                    creation.deliveryId(), creation.challengeId(), exception.code(), failedAt));
            throw exception;
        }

        return new AuthDtos.RegistrationChallengeResponse(
                challengeExternalId,
                toInstant(expiresAt),
                properties.getVerification().getResendCooldown().toSeconds());
    }

    public AuthDtos.PasswordResetChallengeResponse createPasswordResetChallenge(
            AuthDtos.PasswordResetChallengeRequest request,
            ClientRequestMetadata metadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        String emailHash = crypto.digest("normalized-email", normalizedEmail);

        humanVerification.verify(request.humanVerificationToken(), metadata.remoteAddress());
        rateLimiter.consumePasswordReset(emailHash, metadata.deviceHash(), metadata.ipPrefixHash());

        Optional<AuthModels.LoginAccount> account = repository.findLoginAccount(normalizedEmail)
                .filter(candidate -> List.of("ACTIVE", "LIMITED").contains(candidate.status()));
        Long userId = account.map(AuthModels.LoginAccount::userId).orElse(null);
        String challengeExternalId = crypto.newExternalId();
        String code = crypto.newVerificationCode();
        String codeHash = crypto.digest("verification-code:" + challengeExternalId, code);
        LocalDateTime currentTime = now();
        LocalDateTime expiresAt = currentTime.plus(properties.getVerification().getCodeTtl());

        PasswordResetChallengeCreation creation = transactions.execute(status -> {
            repository.supersedeActiveChallenges(normalizedEmail, PASSWORD_RESET);
            long challengeId = repository.insertVerificationChallenge(
                    challengeExternalId,
                    userId,
                    normalizedEmail,
                    PASSWORD_RESET,
                    codeHash,
                    currentTime,
                    expiresAt,
                    properties.getVerification().getMaximumAttempts(),
                    metadata.ipPrefixHash(),
                    metadata.deviceHash());
            if (userId == null) {
                return new PasswordResetChallengeCreation(challengeId, null);
            }
            long deliveryId = repository.insertQueuedDelivery(
                    crypto.newExternalId(), challengeId, userId, "PASSWORD_RESET_CODE", emailHash);
            return new PasswordResetChallengeCreation(challengeId, deliveryId);
        });
        if (creation == null) {
            throw new IllegalStateException("密码重置挑战事务没有返回结果");
        }

        if (creation.deliveryId() != null) {
            try {
                String providerMessageId = emailGateway.sendVerificationCode(
                        request.email().trim(), code, toInstant(expiresAt),
                        EmailGateway.VerificationPurpose.PASSWORD_RESET);
                LocalDateTime sentAt = now();
                transactions.executeWithoutResult(status ->
                        repository.markDeliverySent(creation.deliveryId(), providerMessageId, sentAt));
            } catch (AuthApiException exception) {
                LocalDateTime failedAt = now();
                transactions.executeWithoutResult(status -> repository.markDeliveryFailedAndSupersedeChallenge(
                        creation.deliveryId(), creation.challengeId(), exception.code(), failedAt));
            }
        }

        return new AuthDtos.PasswordResetChallengeResponse(
                challengeExternalId,
                toInstant(expiresAt),
                properties.getVerification().getResendCooldown().toSeconds());
    }

    public AuthDtos.VerificationProofResponse verifyRegistrationEmail(
            String challengeExternalId,
            AuthDtos.VerifyEmailRequest request) {
        String proof = crypto.newOpaqueToken();
        String proofHash = crypto.digest("registration-proof", proof);
        LocalDateTime currentTime = now();

        VerificationResult result = transactions.execute(status -> {
            ChallengeCheck check = checkPendingChallenge(
                    challengeExternalId, REGISTRATION, request.code(), currentTime);
            if (!"VALID".equals(check.status())) {
                return new VerificationResult(check.status(), null);
            }

            LocalDateTime proofExpiresAt = currentTime.plus(properties.getVerification().getProofTtl());
            repository.markChallengeVerified(
                    check.challenge().id(), proofHash, currentTime, proofExpiresAt);
            return new VerificationResult("VERIFIED", proofExpiresAt);
        });

        if (result != null && "VERIFIED".equals(result.status())) {
            return new AuthDtos.VerificationProofResponse(proof, toInstant(result.proofExpiresAt()));
        }
        throw verificationFailure(result == null ? "NOT_FOUND" : result.status());
    }

    public AuthDtos.PasswordResetProofResponse verifyPasswordResetEmail(
            String challengeExternalId,
            AuthDtos.PasswordResetVerifyEmailRequest request,
            ClientRequestMetadata metadata) {
        String resetToken = crypto.newOpaqueToken();
        String proofHash = crypto.digest("password-reset-proof", resetToken);
        String tokenHash = crypto.digest("password-reset-token", resetToken);
        LocalDateTime currentTime = now();

        VerificationResult result = transactions.execute(status -> {
            ChallengeCheck check = checkPendingChallenge(
                    challengeExternalId, PASSWORD_RESET, request.code(), currentTime);
            if (!"VALID".equals(check.status())) {
                return new VerificationResult(check.status(), null);
            }
            AuthModels.VerificationChallenge challenge = check.challenge();
            if (challenge.userId() == null) {
                repository.recordInvalidCode(challenge.id(), challenge.maximumAttempts(), true);
                return new VerificationResult("INVALID_CODE", null);
            }

            LocalDateTime proofExpiresAt = currentTime.plus(properties.getVerification().getProofTtl());
            repository.markChallengeVerified(challenge.id(), proofHash, currentTime, proofExpiresAt);
            long sessionVersion = repository.lockSessionVersion(challenge.userId());
            repository.revokeActivePasswordResetTokens(challenge.userId());
            repository.insertPasswordResetToken(
                    crypto.newExternalId(), challenge.userId(), tokenHash, proofExpiresAt,
                    metadata.ipPrefixHash(), sessionVersion, currentTime);
            repository.consumeChallenge(challenge.id(), challenge.userId(), currentTime);
            return new VerificationResult("VERIFIED", proofExpiresAt);
        });

        if (result != null && "VERIFIED".equals(result.status())) {
            return new AuthDtos.PasswordResetProofResponse(
                    resetToken, toInstant(result.proofExpiresAt()));
        }
        throw verificationFailure(result == null ? "NOT_FOUND" : result.status());
    }

    public AuthModels.IssuedSession register(
            AuthDtos.RegisterRequest request,
            ClientRequestMetadata metadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        String normalizedPassword = passwordPolicy.normalizeAndValidate(request.password(), normalizedEmail);
        String passwordHash = passwordPolicy.encode(normalizedPassword);
        String proofHash = crypto.digest("registration-proof", request.registrationProof());
        String displayName = defaultDisplayName(normalizedEmail);
        SessionSecrets secrets = newSessionSecrets();
        LocalDateTime currentTime = now();

        try {
            AuthModels.IssuedSession issued = transactions.execute(status -> {
                AuthModels.LegalDocumentVersion termsVersion = repository
                        .findActiveTermsVersionForUpdate(request.termsVersion(), "zh-CN", currentTime)
                        .orElseThrow(this::legalDocumentVersionOutdated);
                AuthModels.LegalDocumentVersion privacyVersion = repository
                        .findActivePrivacyVersionForUpdate(request.privacyVersion(), "zh-CN", currentTime)
                        .orElseThrow(this::legalDocumentVersionOutdated);
                AuthModels.VerificationChallenge challenge = repository
                        .findVerifiedChallengeForUpdate(normalizedEmail, REGISTRATION, proofHash)
                        .orElseThrow(() -> new AuthApiException(HttpStatus.UNAUTHORIZED,
                                "INVALID_REGISTRATION_PROOF", "注册证明无效或已经使用。"));
                if (!"VERIFIED".equals(challenge.status())
                        || challenge.proofExpiresAt() == null
                        || !challenge.proofExpiresAt().isAfter(currentTime)) {
                    throw new AuthApiException(HttpStatus.UNAUTHORIZED,
                            "INVALID_REGISTRATION_PROOF", "注册证明无效或已经过期。");
                }
                if (challenge.userId() != null || repository.userExists(normalizedEmail)) {
                    throw new AuthApiException(HttpStatus.CONFLICT, "EMAIL_UNAVAILABLE",
                            "该邮箱无法用于新账户注册。");
                }

                String userExternalId = crypto.newExternalId();
                long userId = repository.insertUser(
                        userExternalId, normalizedEmail, request.email().trim(), currentTime);
                repository.insertCredential(
                        crypto.newExternalId(), userId, passwordHash,
                        PasswordPolicy.HASH_POLICY_KEY, currentTime);
                repository.insertProfile(crypto.newExternalId(), userId, displayName);
                repository.insertSettings(crypto.newExternalId(), userId);
                repository.insertTermsAcceptance(
                        crypto.newExternalId(), userId, termsVersion.id(),
                        metadata.ipPrefixHash(), metadata.userAgentHash(), currentTime);
                repository.insertPrivacyAcknowledgement(
                        crypto.newExternalId(), userId, privacyVersion.id(),
                        metadata.ipPrefixHash(), metadata.userAgentHash(), currentTime);
                AuthModels.Device device = repository.findOrCreateDevice(
                        crypto.newExternalId(), userId, metadata.deviceHash(), "TRUSTED", currentTime);
                AuthModels.IssuedSession session = createSession(
                        userId, userExternalId, device.id(), request.email().trim(), displayName,
                        metadata, "PRIMARY", secrets, currentTime);
                repository.consumeChallenge(challenge.id(), userId, currentTime);
                repository.insertSecurityEvent(
                        crypto.newExternalId(), userId, null, "REGISTRATION_COMPLETED", "INFO",
                        crypto.digest("normalized-email", normalizedEmail), metadata.ipPrefixHash(), currentTime);
                return session;
            });
            if (issued == null) {
                throw new IllegalStateException("注册事务没有返回 Session");
            }
            return issued;
        } catch (DuplicateKeyException exception) {
            throw new AuthApiException(HttpStatus.CONFLICT, "EMAIL_UNAVAILABLE",
                    "该邮箱无法用于新账户注册。");
        }
    }

    public void resetPassword(
            AuthDtos.PasswordResetRequest request,
            ClientRequestMetadata metadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        String normalizedPassword = passwordPolicy.normalizeAndValidate(
                request.newPassword(), normalizedEmail);
        String passwordHash = passwordPolicy.encode(normalizedPassword);
        String resetTokenHash = crypto.digest("password-reset-token", request.passwordResetToken());
        String emailHash = crypto.digest("normalized-email", normalizedEmail);
        LocalDateTime currentTime = now();

        PasswordResetResult result = transactions.execute(status -> {
            Optional<AuthModels.PasswordResetToken> optional =
                    repository.findPasswordResetTokenForUpdate(resetTokenHash, normalizedEmail);
            if (optional.isEmpty()) {
                return new PasswordResetResult("NOT_FOUND");
            }
            AuthModels.PasswordResetToken token = optional.get();
            if (!"ACTIVE".equals(token.status())) {
                return new PasswordResetResult("NOT_ACTIVE");
            }
            if (!token.expiresAt().isAfter(currentTime)) {
                repository.expirePasswordResetToken(token.id());
                return new PasswordResetResult("EXPIRED");
            }
            if (token.sessionVersionAtIssue() != token.currentSessionVersion()) {
                return new PasswordResetResult("NOT_ACTIVE");
            }
            if (!List.of("ACTIVE", "LIMITED").contains(token.userStatus())) {
                return new PasswordResetResult("ACCOUNT_UNAVAILABLE");
            }
            if (passwordPolicy.matches(normalizedPassword, token.passwordHash())) {
                return new PasswordResetResult("SAME_PASSWORD");
            }

            repository.updatePasswordCredential(
                    token.userId(), passwordHash, PasswordPolicy.HASH_POLICY_KEY, currentTime);
            repository.consumePasswordResetToken(token.id(), currentTime);
            repository.revokeAllSessions(token.userId(), "PASSWORD_RESET", currentTime);
            repository.insertSecurityEvent(
                    crypto.newExternalId(), token.userId(), null,
                    "PASSWORD_RESET_COMPLETED", "INFO", emailHash,
                    metadata.ipPrefixHash(), currentTime);
            return new PasswordResetResult("COMPLETED");
        });

        String status = result == null ? "NOT_FOUND" : result.status();
        switch (status) {
            case "COMPLETED" -> {
                return;
            }
            case "SAME_PASSWORD" -> throw new AuthApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PASSWORD_UNCHANGED",
                    "新密码不能与当前密码相同。");
            case "ACCOUNT_UNAVAILABLE" -> throw new AuthApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_UNAVAILABLE",
                    "该账户当前无法重置密码。");
            case "EXPIRED" -> throw new AuthApiException(
                    HttpStatus.CONFLICT,
                    "PASSWORD_RESET_EXPIRED",
                    "密码重置凭证已过期，请重新获取验证码。");
            default -> throw new AuthApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD_RESET_TOKEN",
                    "密码重置凭证无效或已经使用，请重新获取验证码。");
        }
    }

    public AuthModels.IssuedSession login(
            AuthDtos.LoginRequest request,
            ClientRequestMetadata metadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        String emailHash = crypto.digest("normalized-email", normalizedEmail);
        rateLimiter.assertLoginAllowed(emailHash, metadata.ipPrefixHash());

        if (rateLimiter.requiresHumanVerificationForLogin(emailHash)) {
            if (request.humanVerificationToken() == null
                    || request.humanVerificationToken().isBlank()) {
                throw new AuthApiException(HttpStatus.FORBIDDEN,
                        "HUMAN_VERIFICATION_REQUIRED", "请先完成人机验证后再登录。");
            }
            humanVerification.verify(request.humanVerificationToken(), metadata.remoteAddress());
        }

        Optional<AuthModels.LoginAccount> optionalAccount = repository.findLoginAccount(normalizedEmail);
        boolean passwordMatches = passwordPolicy.matches(
                request.password(), optionalAccount.map(AuthModels.LoginAccount::passwordHash).orElse(null));
        if (optionalAccount.isEmpty() || !passwordMatches) {
            rateLimiter.recordLoginFailure(emailHash, metadata.ipPrefixHash());
            transactions.executeWithoutResult(status -> repository.insertSecurityEvent(
                    crypto.newExternalId(), optionalAccount.map(AuthModels.LoginAccount::userId).orElse(null),
                    null, "LOGIN_FAILED", "WARN", emailHash, metadata.ipPrefixHash(), now()));
            throw new AuthApiException(HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS", "邮箱或密码错误。");
        }

        AuthModels.LoginAccount account = optionalAccount.get();
        if (!List.of("ACTIVE", "LIMITED").contains(account.status())) {
            throw new AuthApiException(HttpStatus.FORBIDDEN,
                    "ACCOUNT_UNAVAILABLE", "该账户当前无法登录。");
        }

        rateLimiter.recordLoginSuccess(emailHash);
        SessionSecrets secrets = newSessionSecrets();
        LocalDateTime currentTime = now();
        AuthModels.IssuedSession issued = transactions.execute(status -> {
            AuthModels.Device device = repository.findOrCreateDevice(
                    crypto.newExternalId(), account.userId(), metadata.deviceHash(),
                    "UNVERIFIED", currentTime);
            if ("REVOKED".equals(device.status())) {
                throw new AuthApiException(HttpStatus.FORBIDDEN,
                        "DEVICE_REVOKED", "该设备已被撤销，请通过账户恢复流程处理。");
            }
            repository.updateLastLogin(account.userId(), currentTime);
            AuthModels.IssuedSession session = createSession(
                    account.userId(), account.userExternalId(), device.id(),
                    account.emailDisplay(), account.displayName(),
                    metadata, "PRIMARY", secrets, currentTime);
            repository.insertSecurityEvent(
                    crypto.newExternalId(), account.userId(), null, "LOGIN_SUCCEEDED", "INFO",
                    emailHash, metadata.ipPrefixHash(), currentTime);
            return session;
        });
        if (issued == null) {
            throw new IllegalStateException("登录事务没有返回 Session");
        }
        return issued;
    }

    public AuthModels.AuthenticatedSession authenticate(String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            throw unauthenticated();
        }
        String tokenHash = crypto.digest("session-token", rawSessionToken);
        LocalDateTime currentTime = now();

        AuthModels.AuthenticatedSession authenticated = transactions.execute(status -> {
            AuthModels.SessionRecord session = repository.findSessionByTokenHash(tokenHash)
                    .orElseThrow(this::unauthenticated);
            if (!List.of("ACTIVE", "LIMITED").contains(session.userStatus())) {
                throw unauthenticated();
            }
            if (!session.idleExpiresAt().isAfter(currentTime)
                    || !session.absoluteExpiresAt().isAfter(currentTime)) {
                repository.markSessionExpired(session.id());
                return null;
            }

            LocalDateTime newIdleExpiry = minimum(
                    currentTime.plus(properties.getSession().getIdleTimeout()),
                    session.absoluteExpiresAt());
            String rotatedToken = null;
            String rotatedCsrf = null;
            String effectiveCsrfHash = session.csrfSecretHash();

            if (!session.tokenRotatedAt()
                    .plus(properties.getSession().getRotationInterval())
                    .isAfter(currentTime)) {
                rotatedToken = crypto.newOpaqueToken();
                rotatedCsrf = crypto.newOpaqueToken();
                effectiveCsrfHash = crypto.digest("csrf-token", rotatedCsrf);
                boolean rotated = repository.rotateSession(
                        session.id(), session.rowVersion(),
                        crypto.digest("session-token", rotatedToken), effectiveCsrfHash,
                        currentTime, newIdleExpiry);
                if (!rotated) {
                    throw unauthenticated();
                }
            } else if (!session.lastSeenAt()
                    .plus(properties.getSession().getActivityWriteInterval())
                    .isAfter(currentTime)) {
                repository.touchSession(session.id(), currentTime, newIdleExpiry);
            }

            return new AuthModels.AuthenticatedSession(
                    session.id(), session.externalId(), session.userId(), session.userExternalId(),
                    effectiveCsrfHash, rotatedToken, rotatedCsrf,
                    sessionResponse(session.userExternalId(), session.emailDisplay(),
                            session.displayName(), session.authLevel(),
                            newIdleExpiry, session.absoluteExpiresAt()));
        });

        if (authenticated == null) {
            throw unauthenticated();
        }
        return authenticated;
    }

    public String refreshCsrf(AuthModels.AuthenticatedSession session) {
        String csrf = crypto.newOpaqueToken();
        boolean updated = transactions.execute(status ->
                repository.rotateCsrf(session.sessionId(), crypto.digest("csrf-token", csrf)));
        if (!updated) {
            throw unauthenticated();
        }
        return csrf;
    }

    public void logout(AuthModels.AuthenticatedSession session) {
        LocalDateTime currentTime = now();
        transactions.executeWithoutResult(status -> {
            repository.revokeSession(session.sessionId(), "USER_LOGOUT", currentTime);
            repository.insertSecurityEvent(
                    crypto.newExternalId(), session.userId(), session.sessionId(),
                    "SESSION_REVOKED", "INFO", null, null, currentTime);
        });
    }

    private AuthModels.IssuedSession createSession(
            long userId,
            String userExternalId,
            long deviceId,
            String emailDisplay,
            String displayName,
            ClientRequestMetadata metadata,
            String authLevel,
            SessionSecrets secrets,
            LocalDateTime currentTime) {
        repository.expireElapsedSessions(userId, currentTime);
        List<Long> activeSessions = new ArrayList<>(repository.lockActiveSessionIds(userId));
        int maximum = properties.getSession().getMaximumActiveSessions();
        while (activeSessions.size() >= maximum) {
            repository.revokeSession(activeSessions.remove(0), "SESSION_LIMIT_REACHED", currentTime);
        }

        LocalDateTime idleExpiresAt = currentTime.plus(properties.getSession().getIdleTimeout());
        LocalDateTime absoluteExpiresAt = currentTime.plus(properties.getSession().getAbsoluteTimeout());
        repository.insertSession(
                crypto.newExternalId(), userId, deviceId,
                crypto.digest("session-token", secrets.sessionToken()),
                crypto.digest("csrf-token", secrets.csrfToken()),
                authLevel, metadata.ipPrefixHash(), metadata.userAgentHash(),
                currentTime, idleExpiresAt, absoluteExpiresAt);
        return new AuthModels.IssuedSession(
                secrets.sessionToken(),
                secrets.csrfToken(),
                sessionResponse(userExternalId, emailDisplay, displayName, authLevel,
                        idleExpiresAt, absoluteExpiresAt));
    }

    private AuthDtos.SessionResponse sessionResponse(
            String userExternalId,
            String email,
            String displayName,
            String authLevel,
            LocalDateTime idleExpiresAt,
            LocalDateTime absoluteExpiresAt) {
        return new AuthDtos.SessionResponse(
                userExternalId,
                email,
                displayName,
                authLevel,
                toInstant(idleExpiresAt),
                toInstant(absoluteExpiresAt));
    }

    private String defaultDisplayName(String normalizedEmail) {
        String value = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        if (value.isBlank()) {
            value = "ExamInsight 用户";
        }
        int end = value.offsetByCodePoints(0, Math.min(80, value.codePointCount(0, value.length())));
        return value.substring(0, end);
    }

    private SessionSecrets newSessionSecrets() {
        return new SessionSecrets(crypto.newOpaqueToken(), crypto.newOpaqueToken());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime minimum(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private ChallengeCheck checkPendingChallenge(
            String challengeExternalId,
            String purpose,
            String code,
            LocalDateTime currentTime) {
        Optional<AuthModels.VerificationChallenge> optional =
                repository.findChallengeForUpdate(challengeExternalId, purpose);
        if (optional.isEmpty()) {
            return new ChallengeCheck("NOT_FOUND", null);
        }
        AuthModels.VerificationChallenge challenge = optional.get();
        if (!"PENDING".equals(challenge.status())) {
            return new ChallengeCheck(challenge.status(), null);
        }
        if (!challenge.expiresAt().isAfter(currentTime)) {
            repository.markChallengeExpired(challenge.id());
            return new ChallengeCheck("EXPIRED", null);
        }
        if (!crypto.matches(
                "verification-code:" + challenge.externalId(), code, challenge.codeHash())) {
            int attempts = challenge.attemptCount() + 1;
            boolean locked = attempts >= challenge.maximumAttempts();
            repository.recordInvalidCode(challenge.id(), attempts, locked);
            return new ChallengeCheck(locked ? "LOCKED" : "INVALID_CODE", null);
        }
        return new ChallengeCheck("VALID", challenge);
    }

    private AuthApiException verificationFailure(String status) {
        return switch (status) {
            case "INVALID_CODE" -> new AuthApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_CODE", "验证码不正确。");
            case "LOCKED" -> new AuthApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_LOCKED",
                    "验证码错误次数过多，请重新获取。");
            case "EXPIRED" -> new AuthApiException(
                    HttpStatus.CONFLICT, "VERIFICATION_EXPIRED",
                    "验证码已过期，请重新获取。");
            case "NOT_FOUND" -> new AuthApiException(
                    HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND",
                    "验证码挑战不存在或已不可用。");
            default -> new AuthApiException(
                    HttpStatus.CONFLICT, "CHALLENGE_NOT_PENDING",
                    "该验证码挑战已经处理，请重新获取。");
        };
    }

    private AuthApiException unauthenticated() {
        return new AuthApiException(HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID", "登录状态已失效，请重新登录。");
    }

    private AuthApiException legalDocumentVersionOutdated() {
        return new AuthApiException(
                HttpStatus.CONFLICT,
                "LEGAL_DOCUMENT_VERSION_OUTDATED",
                "用户协议或隐私政策已经更新，请刷新页面并重新确认。");
    }

    private record ChallengeCreation(long challengeId, long deliveryId) {
    }

    private record PasswordResetChallengeCreation(long challengeId, Long deliveryId) {
    }

    private record ChallengeCheck(String status, AuthModels.VerificationChallenge challenge) {
    }

    private record VerificationResult(String status, LocalDateTime proofExpiresAt) {
    }

    private record PasswordResetResult(String status) {
    }

    private record SessionSecrets(String sessionToken, String csrfToken) {
    }
}
