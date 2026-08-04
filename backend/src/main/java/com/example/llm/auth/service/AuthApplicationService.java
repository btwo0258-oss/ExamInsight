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

import java.text.Normalizer;
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
                repository.supersedeActiveRegistrationChallenges(normalizedEmail);
                long challengeId = repository.insertRegistrationChallenge(
                        challengeExternalId,
                        normalizedEmail,
                        codeHash,
                        expiresAt,
                        properties.getVerification().getMaximumAttempts(),
                        metadata.ipPrefixHash(),
                        metadata.deviceHash());
                long deliveryId = repository.insertQueuedDelivery(
                        deliveryExternalId, challengeId, emailHash);
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
            String providerMessageId = emailGateway.sendRegistrationCode(
                    request.email().trim(), code, toInstant(expiresAt));
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

    public AuthDtos.VerificationProofResponse verifyRegistrationEmail(
            String challengeExternalId,
            AuthDtos.VerifyEmailRequest request) {
        String proof = crypto.newOpaqueToken();
        String proofHash = crypto.digest("registration-proof", proof);
        LocalDateTime currentTime = now();

        VerificationResult result = transactions.execute(status -> {
            Optional<AuthModels.VerificationChallenge> optional =
                    repository.findChallengeForUpdate(challengeExternalId);
            if (optional.isEmpty()) {
                return VerificationResult.notFound();
            }
            AuthModels.VerificationChallenge challenge = optional.get();
            if (!"PENDING".equals(challenge.status())) {
                return new VerificationResult(challenge.status(), null);
            }
            if (!challenge.expiresAt().isAfter(currentTime)) {
                repository.markChallengeExpired(challenge.id());
                return new VerificationResult("EXPIRED", null);
            }
            if (!crypto.matches(
                    "verification-code:" + challenge.externalId(),
                    request.code(),
                    challenge.codeHash())) {
                int attempts = challenge.attemptCount() + 1;
                boolean locked = attempts >= challenge.maximumAttempts();
                repository.recordInvalidCode(challenge.id(), attempts, locked);
                return new VerificationResult(locked ? "LOCKED" : "INVALID_CODE", null);
            }

            LocalDateTime proofExpiresAt = currentTime.plus(properties.getVerification().getProofTtl());
            repository.markChallengeVerified(
                    challenge.id(), proofHash, currentTime, proofExpiresAt);
            return new VerificationResult("VERIFIED", proofExpiresAt);
        });

        if (result == null || "NOT_FOUND".equals(result.status())) {
            throw new AuthApiException(HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND",
                    "验证码挑战不存在或已不可用。");
        }
        return switch (result.status()) {
            case "VERIFIED" -> new AuthDtos.VerificationProofResponse(
                    proof, toInstant(result.proofExpiresAt()));
            case "INVALID_CODE" -> throw new AuthApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_VERIFICATION_CODE", "验证码不正确。");
            case "LOCKED" -> throw new AuthApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "VERIFICATION_LOCKED", "验证码错误次数过多，请重新获取。");
            case "EXPIRED" -> throw new AuthApiException(HttpStatus.CONFLICT,
                    "VERIFICATION_EXPIRED", "验证码已过期，请重新获取。");
            default -> throw new AuthApiException(HttpStatus.CONFLICT,
                    "CHALLENGE_NOT_PENDING", "该验证码挑战已经处理，请重新获取。");
        };
    }

    public AuthModels.IssuedSession register(
            AuthDtos.RegisterRequest request,
            ClientRequestMetadata metadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        String normalizedPassword = passwordPolicy.normalizeAndValidate(request.password(), normalizedEmail);
        String passwordHash = passwordPolicy.encode(normalizedPassword);
        String proofHash = crypto.digest("registration-proof", request.registrationProof());
        String displayName = normalizeDisplayName(request.displayName(), normalizedEmail);
        SessionSecrets secrets = newSessionSecrets();
        LocalDateTime currentTime = now();

        try {
            AuthModels.IssuedSession issued = transactions.execute(status -> {
                AuthModels.VerificationChallenge challenge = repository
                        .findVerifiedChallengeForUpdate(normalizedEmail, proofHash)
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

    public void logoutAll(AuthModels.AuthenticatedSession session) {
        LocalDateTime currentTime = now();
        transactions.executeWithoutResult(status -> {
            repository.revokeAllSessions(session.userId(), "USER_LOGOUT_ALL", currentTime);
            repository.insertSecurityEvent(
                    crypto.newExternalId(), session.userId(), null,
                    "ALL_SESSIONS_REVOKED", "INFO", null, null, currentTime);
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

    private String normalizeDisplayName(String rawDisplayName, String normalizedEmail) {
        String value = rawDisplayName == null || rawDisplayName.isBlank()
                ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                : Normalizer.normalize(rawDisplayName.trim(), Normalizer.Form.NFC);
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

    private AuthApiException unauthenticated() {
        return new AuthApiException(HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID", "登录状态已失效，请重新登录。");
    }

    private record ChallengeCreation(long challengeId, long deliveryId) {
    }

    private record VerificationResult(String status, LocalDateTime proofExpiresAt) {
        private static VerificationResult notFound() {
            return new VerificationResult("NOT_FOUND", null);
        }
    }

    private record SessionSecrets(String sessionToken, String csrfToken) {
    }
}
