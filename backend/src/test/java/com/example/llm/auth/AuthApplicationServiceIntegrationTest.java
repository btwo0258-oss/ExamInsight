package com.example.llm.auth;

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
import com.example.llm.auth.service.AuthApplicationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class AuthApplicationServiceIntegrationTest {
    private static final String EMAIL = "student@example.com";
    private static final String PASSWORD = "Correct horse battery staple 2026";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("examinsight_v2_auth_test")
            .withUsername("examinsight")
            .withPassword("examinsight-test-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static JdbcTemplate jdbc;
    private static LettuceConnectionFactory redisConnectionFactory;
    private static StringRedisTemplate redis;
    private AuthProperties properties;
    private AuthCrypto crypto;
    private AuthApplicationService service;
    private AtomicReference<String> deliveredCode;
    private MutableClock clock;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        redisConnectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(redisConnectionFactory);
    }

    @AfterAll
    static void closeRedis() {
        if (redisConnectionFactory != null) {
            redisConnectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        clearIdentityData();
        flushRedis();
        properties = new AuthProperties();
        properties.setHashSecret("integration-test-auth-hmac-secret-at-least-32-characters");
        crypto = new AuthCrypto(properties);
        AuthRepository repository = new AuthRepository(jdbc);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        AuthRateLimiter rateLimiter = new AuthRateLimiter(redis, properties);
        HumanVerificationGateway humanVerification = (token, remoteAddress) -> {
        };
        deliveredCode = new AtomicReference<>();
        EmailGateway emailGateway = (recipient, code, expiresAt) -> {
            deliveredCode.set(code);
            return "test-provider-message";
        };
        clock = new MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        service = new AuthApplicationService(
                repository,
                transactions,
                properties,
                crypto,
                new PasswordPolicy(),
                new EmailNormalizer(),
                rateLimiter,
                humanVerification,
                emailGateway,
                clock);
    }

    @Test
    void registrationProofIsSingleUseAndSessionTokenNeverPersistsInPlaintext() {
        AuthDtos.RegistrationChallengeResponse challenge = createChallenge(EMAIL, "device-registration-0001");
        assertThat(deliveredCode.get()).matches("\\d{6}");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM email_delivery WHERE verification_id = (SELECT id FROM email_verification WHERE external_id = ?)",
                String.class, challenge.challengeId())).isEqualTo("SENT");

        AuthDtos.VerificationProofResponse verification = service.verifyRegistrationEmail(
                challenge.challengeId(), new AuthDtos.VerifyEmailRequest(deliveredCode.get()));
        AuthModels.IssuedSession issued = service.register(
                new AuthDtos.RegisterRequest(
                        EMAIL, PASSWORD, "王同学", true,
                        verification.registrationProof(), "device-registration-0001"),
                metadata("device-registration-0001"));

        String storedTokenHash = jdbc.queryForObject(
                "SELECT token_hash FROM auth_session WHERE external_id IS NOT NULL LIMIT 1", String.class);
        assertThat(storedTokenHash)
                .isNotEqualTo(issued.sessionToken())
                .isEqualTo(crypto.digest("session-token", issued.sessionToken()));
        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM user_credential LIMIT 1", String.class))
                .startsWith("$argon2id$")
                .doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM email_verification WHERE external_id = ?",
                String.class, challenge.challengeId())).isEqualTo("CONSUMED");

        assertThatThrownBy(() -> service.register(
                new AuthDtos.RegisterRequest(
                        EMAIL, PASSWORD, "王同学", true,
                        verification.registrationProof(), "device-registration-0001"),
                metadata("device-registration-0001")))
                .isInstanceOf(AuthApiException.class)
                .extracting(exception -> ((AuthApiException) exception).code())
                .isEqualTo("INVALID_REGISTRATION_PROOF");

        AuthModels.AuthenticatedSession authenticated = service.authenticate(issued.sessionToken());
        String csrf = service.refreshCsrf(authenticated);
        assertThat(jdbc.queryForObject(
                "SELECT csrf_secret_hash FROM auth_session WHERE id = ?",
                String.class, authenticated.sessionId()))
                .isEqualTo(crypto.digest("csrf-token", csrf));

        service.logout(authenticated);
        assertThatThrownBy(() -> service.authenticate(issued.sessionToken()))
                .isInstanceOf(AuthApiException.class)
                .extracting(exception -> ((AuthApiException) exception).code())
                .isEqualTo("SESSION_INVALID");
    }

    @Test
    void verificationCodeLocksAfterFiveWrongAttempts() {
        AuthDtos.RegistrationChallengeResponse challenge = createChallenge(EMAIL, "device-verification-0001");
        int different = (Integer.parseInt(deliveredCode.get()) + 1) % 1_000_000;
        String wrongCode = "%06d".formatted(different);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> service.verifyRegistrationEmail(
                    challenge.challengeId(), new AuthDtos.VerifyEmailRequest(wrongCode)))
                    .isInstanceOf(AuthApiException.class);
        }

        assertThat(jdbc.queryForMap("""
                SELECT status, attempt_count
                  FROM email_verification
                 WHERE external_id = ?
                """, challenge.challengeId()))
                .containsEntry("status", "LOCKED")
                .containsEntry("attempt_count", 5L);
    }

    @Test
    void accountKeepsOnlyFiveActiveSessions() {
        AuthModels.IssuedSession initial = registerAccount();
        service.logout(service.authenticate(initial.sessionToken()));

        for (int index = 0; index < 6; index++) {
            String deviceId = "device-login-%08d".formatted(index);
            service.login(
                    new AuthDtos.LoginRequest(EMAIL, PASSWORD, deviceId, null),
                    metadata(deviceId));
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE status = 'ACTIVE'", Long.class)).isEqualTo(5L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE status = 'REVOKED'", Long.class)).isEqualTo(2L);
    }

    @Test
    void activeSessionRotatesAfterTwentyFourHoursWithoutExceedingAbsoluteExpiry() {
        AuthModels.IssuedSession issued = registerAccount();
        clock.advance(Duration.ofHours(23));
        service.authenticate(issued.sessionToken());

        clock.advance(Duration.ofHours(2));
        AuthModels.AuthenticatedSession rotated = service.authenticate(issued.sessionToken());
        assertThat(rotated.rotatedSessionToken()).isNotBlank().isNotEqualTo(issued.sessionToken());
        assertThat(rotated.rotatedCsrfToken()).isNotBlank();
        assertThatThrownBy(() -> service.authenticate(issued.sessionToken()))
                .isInstanceOf(AuthApiException.class);
        assertThat(service.authenticate(rotated.rotatedSessionToken()).userExternalId())
                .isEqualTo(issued.response().userId());
    }

    @Test
    void resendCooldownAndLoginEscalationAreEnforcedByRedis() {
        createChallenge(EMAIL, "device-rate-limit-0001");
        assertThatThrownBy(() -> createChallenge(EMAIL, "device-rate-limit-0001"))
                .isInstanceOf(AuthApiException.class)
                .extracting(exception -> ((AuthApiException) exception).code())
                .isEqualTo("VERIFICATION_RESEND_COOLDOWN");

        clearIdentityData();
        flushRedis();
        registerAccount();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.login(
                    new AuthDtos.LoginRequest(
                            EMAIL, "Wrong password value 2026", "device-login-rate-0001", null),
                    metadata("device-login-rate-0001")))
                    .isInstanceOf(AuthApiException.class)
                    .extracting(exception -> ((AuthApiException) exception).code())
                    .isEqualTo("INVALID_CREDENTIALS");
        }
        assertThatThrownBy(() -> service.login(
                new AuthDtos.LoginRequest(
                        EMAIL, PASSWORD, "device-login-rate-0001", null),
                metadata("device-login-rate-0001")))
                .isInstanceOf(AuthApiException.class)
                .extracting(exception -> ((AuthApiException) exception).code())
                .isEqualTo("HUMAN_VERIFICATION_REQUIRED");
    }

    private AuthModels.IssuedSession registerAccount() {
        AuthDtos.RegistrationChallengeResponse challenge = createChallenge(EMAIL, "device-registration-0001");
        AuthDtos.VerificationProofResponse proof = service.verifyRegistrationEmail(
                challenge.challengeId(), new AuthDtos.VerifyEmailRequest(deliveredCode.get()));
        return service.register(
                new AuthDtos.RegisterRequest(
                        EMAIL, PASSWORD, "王同学", true,
                        proof.registrationProof(), "device-registration-0001"),
                metadata("device-registration-0001"));
    }

    private AuthDtos.RegistrationChallengeResponse createChallenge(String email, String deviceId) {
        return service.createRegistrationChallenge(
                new AuthDtos.RegistrationChallengeRequest(email, "human-proof", deviceId),
                metadata(deviceId));
    }

    private ClientRequestMetadata metadata(String deviceId) {
        return new ClientRequestMetadata(
                "127.0.0.1",
                crypto.digest("ip-prefix", "127.0.0.0"),
                crypto.digest("user-agent", "integration-test"),
                crypto.digest("device-fingerprint", deviceId));
    }

    private void clearIdentityData() {
        for (String table : new String[]{
                "security_event", "auth_session", "user_device", "user_setting",
                "user_profile", "user_credential", "email_delivery",
                "email_verification", "password_reset_token", "app_user"}) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private void flushRedis() {
        try (org.springframework.data.redis.connection.RedisConnection connection =
                     redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
