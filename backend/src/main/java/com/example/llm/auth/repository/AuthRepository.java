package com.example.llm.auth.repository;

import com.example.llm.auth.domain.AuthModels;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AuthRepository {
    private static final RowMapper<AuthModels.VerificationChallenge> CHALLENGE_MAPPER = (rs, rowNum) ->
            new AuthModels.VerificationChallenge(
                    rs.getLong("id"),
                    rs.getString("external_id"),
                    nullableLong(rs.getObject("user_id")),
                    rs.getString("normalized_email"),
                    rs.getString("code_hash"),
                    rs.getString("verification_proof_hash"),
                    rs.getString("status"),
                    rs.getObject("expires_at", LocalDateTime.class),
                    rs.getObject("verified_at", LocalDateTime.class),
                    rs.getObject("proof_expires_at", LocalDateTime.class),
                    rs.getInt("attempt_count"),
                    rs.getInt("max_attempts"));

    private final JdbcTemplate jdbc;

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public AuthRepository(@Qualifier("v2JdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean userExists(String normalizedEmail) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE normalized_email = ? AND status <> 'PURGED'",
                Integer.class,
                normalizedEmail);
        return count != null && count > 0;
    }

    public void supersedeActiveChallenges(String normalizedEmail, String purpose) {
        jdbc.update("""
                UPDATE email_verification
                   SET status = 'SUPERSEDED', row_version = row_version + 1
                 WHERE normalized_email = ?
                   AND purpose = ?
                   AND status IN ('PENDING', 'VERIFIED')
                """, normalizedEmail, purpose);
    }

    public long insertVerificationChallenge(
            String externalId,
            Long userId,
            String normalizedEmail,
            String purpose,
            String codeHash,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            int maximumAttempts,
            String ipHash,
            String deviceHash) {
        return insertAndReturnId("""
                INSERT INTO email_verification (
                    external_id, user_id, normalized_email, purpose, code_hash,
                    verification_proof_hash, status, expires_at, verified_at,
                    proof_expires_at, attempt_count, max_attempts, consumed_at,
                    request_ip_hash, device_hash, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, 'PENDING', ?, NULL, NULL, 0, ?, NULL, ?, ?, ?, ?)
                """, externalId, userId, normalizedEmail, purpose, codeHash, expiresAt,
                maximumAttempts, ipHash, deviceHash,
                createdAt, createdAt);
    }

    public long insertQueuedDelivery(
            String externalId,
            long verificationId,
            Long userId,
            String templateKey,
            String recipientHash) {
        return insertAndReturnId("""
                INSERT INTO email_delivery (
                    external_id, verification_id, user_id, template_key,
                    recipient_hash, provider_key, provider_message_id,
                    status, attempt_count, sent_at, delivered_at, failed_at, failure_code
                ) VALUES (?, ?, ?, ?, ?, 'SMTP', NULL,
                          'QUEUED', 0, NULL, NULL, NULL, NULL)
                """, externalId, verificationId, userId, templateKey, recipientHash);
    }

    public void markDeliverySent(long deliveryId, String providerMessageId, LocalDateTime sentAt) {
        jdbc.update("""
                UPDATE email_delivery
                   SET status = 'SENT', attempt_count = 1, provider_message_id = ?,
                       sent_at = ?, failed_at = NULL, failure_code = NULL,
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'QUEUED'
                """, providerMessageId, sentAt, deliveryId);
    }

    public void markDeliveryFailedAndSupersedeChallenge(
            long deliveryId, long challengeId, String failureCode, LocalDateTime failedAt) {
        jdbc.update("""
                UPDATE email_delivery
                   SET status = 'FAILED', attempt_count = 1, failed_at = ?, failure_code = ?,
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'QUEUED'
                """, failedAt, failureCode, deliveryId);
        jdbc.update("""
                UPDATE email_verification
                   SET status = 'SUPERSEDED', row_version = row_version + 1
                 WHERE id = ? AND status = 'PENDING'
                """, challengeId);
    }

    public Optional<AuthModels.VerificationChallenge> findChallengeForUpdate(
            String externalId,
            String purpose) {
        return jdbc.query("""
                SELECT id, external_id, user_id, normalized_email, code_hash,
                       verification_proof_hash, status, expires_at, verified_at,
                       proof_expires_at, attempt_count, max_attempts
                  FROM email_verification
                 WHERE external_id = ? AND purpose = ?
                 FOR UPDATE
                """, CHALLENGE_MAPPER, externalId, purpose).stream().findFirst();
    }

    public void markChallengeExpired(long id) {
        jdbc.update("""
                UPDATE email_verification
                   SET status = 'EXPIRED', row_version = row_version + 1
                 WHERE id = ? AND status = 'PENDING'
                """, id);
    }

    public void recordInvalidCode(long id, int attempts, boolean locked) {
        jdbc.update("""
                UPDATE email_verification
                   SET attempt_count = ?, status = ?, row_version = row_version + 1
                 WHERE id = ? AND status = 'PENDING'
                """, attempts, locked ? "LOCKED" : "PENDING", id);
    }

    public void markChallengeVerified(
            long id, String proofHash, LocalDateTime verifiedAt, LocalDateTime proofExpiresAt) {
        jdbc.update("""
                UPDATE email_verification
                   SET status = 'VERIFIED', verification_proof_hash = ?,
                       verified_at = ?, proof_expires_at = ?, row_version = row_version + 1
                 WHERE id = ? AND status = 'PENDING'
                """, proofHash, verifiedAt, proofExpiresAt, id);
    }

    public Optional<AuthModels.VerificationChallenge> findVerifiedChallengeForUpdate(
            String normalizedEmail, String purpose, String proofHash) {
        return jdbc.query("""
                SELECT id, external_id, user_id, normalized_email, code_hash,
                       verification_proof_hash, status, expires_at, verified_at,
                       proof_expires_at, attempt_count, max_attempts
                  FROM email_verification
                 WHERE normalized_email = ?
                   AND purpose = ?
                   AND verification_proof_hash = ?
                 FOR UPDATE
                """, CHALLENGE_MAPPER, normalizedEmail, purpose, proofHash).stream().findFirst();
    }

    public long insertUser(
            String externalId,
            String normalizedEmail,
            String emailDisplay,
            LocalDateTime now) {
        return insertAndReturnId("""
                INSERT INTO app_user (
                    external_id, normalized_email, email_display, status,
                    email_verified_at, last_login_at,
                    session_version, trash_started_at, previous_status, deleted_at
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, 1, NULL, NULL, NULL)
                """, externalId, normalizedEmail, emailDisplay, now, now);
    }

    public void insertCredential(
            String externalId,
            long userId,
            String passwordHash,
            String hashPolicyKey,
            LocalDateTime now) {
        jdbc.update("""
                INSERT INTO user_credential (
                    external_id, user_id, credential_type, password_hash,
                    hash_policy_key, password_changed_at, compromised_checked_at, disabled_at
                ) VALUES (?, ?, 'PASSWORD', ?, ?, ?, NULL, NULL)
                """, externalId, userId, passwordHash, hashPolicyKey, now);
    }

    public void insertProfile(String externalId, long userId, String displayName) {
        jdbc.update("""
                INSERT INTO user_profile (external_id, user_id, display_name, avatar_asset_id, bio)
                VALUES (?, ?, ?, NULL, NULL)
                """, externalId, userId, displayName);
    }

    public void insertSettings(String externalId, long userId) {
        jdbc.update("""
                INSERT INTO user_setting (
                    external_id, user_id, theme, locale, timezone,
                    reduced_motion, email_notification_enabled, learning_reminder_enabled
                ) VALUES (?, ?, 'SYSTEM', 'zh-CN', 'Asia/Shanghai', FALSE, TRUE, TRUE)
                """, externalId, userId);
    }

    public Optional<AuthModels.LegalDocumentVersion> findActiveTermsVersionForUpdate(
            String versionKey,
            String locale,
            LocalDateTime currentTime) {
        return findActiveLegalDocumentVersionForUpdate(
                "terms_document_version", versionKey, locale, currentTime);
    }

    public Optional<AuthModels.LegalDocumentVersion> findActivePrivacyVersionForUpdate(
            String versionKey,
            String locale,
            LocalDateTime currentTime) {
        return findActiveLegalDocumentVersionForUpdate(
                "privacy_notice_version", versionKey, locale, currentTime);
    }

    public void insertTermsAcceptance(
            String externalId,
            long userId,
            long termsVersionId,
            String ipHash,
            String userAgentHash,
            LocalDateTime acceptedAt) {
        jdbc.update("""
                INSERT INTO terms_acceptance (
                    external_id, user_id, terms_version_id, accepted_at,
                    ip_prefix_hash, user_agent_hash, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, externalId, userId, termsVersionId, acceptedAt,
                ipHash, userAgentHash, acceptedAt);
    }

    public void insertPrivacyAcknowledgement(
            String externalId,
            long userId,
            long noticeVersionId,
            String ipHash,
            String userAgentHash,
            LocalDateTime acknowledgedAt) {
        jdbc.update("""
                INSERT INTO privacy_notice_acknowledgement (
                    external_id, user_id, notice_version_id, acknowledged_at,
                    ip_prefix_hash, user_agent_hash, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, externalId, userId, noticeVersionId, acknowledgedAt,
                ipHash, userAgentHash, acknowledgedAt);
    }

    public AuthModels.Device findOrCreateDevice(
            String externalId,
            long userId,
            String deviceHash,
            String trustStatus,
            LocalDateTime now) {
        Optional<AuthModels.Device> existing = jdbc.query("""
                SELECT id, trust_status
                  FROM user_device
                 WHERE user_id = ? AND device_fingerprint_hash = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new AuthModels.Device(rs.getLong("id"), rs.getString("trust_status")),
                userId, deviceHash).stream().findFirst();
        if (existing.isPresent()) {
            jdbc.update("""
                    UPDATE user_device
                       SET last_seen_at = ?, row_version = row_version + 1
                     WHERE id = ?
                    """, now, existing.get().id());
            return existing.get();
        }

        long id = insertAndReturnId("""
                INSERT INTO user_device (
                    external_id, user_id, device_fingerprint_hash, display_name,
                    first_seen_at, last_seen_at, trust_status, risk_level, revoked_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, 'LOW', NULL)
                """, externalId, userId, deviceHash, now, now, trustStatus);
        return new AuthModels.Device(id, trustStatus);
    }

    public void consumeChallenge(long challengeId, long userId, LocalDateTime consumedAt) {
        jdbc.update("""
                UPDATE email_verification
                   SET user_id = ?, status = 'CONSUMED', consumed_at = ?,
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'VERIFIED'
                """, userId, consumedAt, challengeId);
    }

    public Optional<AuthModels.LoginAccount> findLoginAccount(String normalizedEmail) {
        return jdbc.query("""
                SELECT u.id AS user_id, u.external_id AS user_external_id,
                       u.normalized_email, u.email_display, u.status,
                       c.password_hash, p.display_name
                  FROM app_user u
                  JOIN user_credential c
                    ON c.user_id = u.id
                   AND c.credential_type = 'PASSWORD'
                   AND c.disabled_at IS NULL
                  JOIN user_profile p ON p.user_id = u.id
                 WHERE u.normalized_email = ?
                """, (rs, rowNum) -> new AuthModels.LoginAccount(
                        rs.getLong("user_id"),
                        rs.getString("user_external_id"),
                        rs.getString("normalized_email"),
                        rs.getString("email_display"),
                        rs.getString("status"),
                        rs.getString("password_hash"),
                        rs.getString("display_name")), normalizedEmail).stream().findFirst();
    }

    public Optional<AuthModels.AccountCredential> findAccountCredentialForUpdate(long userId) {
        return jdbc.query("""
                SELECT u.id AS user_id, u.external_id AS user_external_id,
                       u.normalized_email, u.email_display, u.status,
                       c.password_hash, p.display_name
                  FROM app_user u
                  JOIN user_credential c
                    ON c.user_id = u.id
                   AND c.credential_type = 'PASSWORD'
                   AND c.disabled_at IS NULL
                  JOIN user_profile p ON p.user_id = u.id
                 WHERE u.id = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new AuthModels.AccountCredential(
                        rs.getLong("user_id"),
                        rs.getString("user_external_id"),
                        rs.getString("normalized_email"),
                        rs.getString("email_display"),
                        rs.getString("status"),
                        rs.getString("password_hash"),
                        rs.getString("display_name")), userId).stream().findFirst();
    }

    public void updateDisplayName(long userId, String displayName) {
        jdbc.update("""
                UPDATE user_profile
                   SET display_name = ?
                 WHERE user_id = ?
                """, displayName, userId);
    }

    public void insertAccountDeletionRequest(
            String externalId,
            long userId,
            String subjectHash,
            String previousUserStatus,
            LocalDateTime requestedAt,
            LocalDateTime purgeScheduledAt) {
        jdbc.update("""
                INSERT INTO account_deletion_request (
                    external_id, user_id, subject_hash, privacy_request_id,
                    status, requested_at, cancellable_until, cancelled_at,
                    purge_scheduled_at, completed_at, safe_failure_code,
                    previous_user_status, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, 'SCHEDULED', ?, ?, NULL, ?, NULL, NULL, ?, ?, ?)
                """, externalId, userId, subjectHash, requestedAt, purgeScheduledAt,
                purgeScheduledAt, previousUserStatus, requestedAt, requestedAt);
    }

    public void trashAccount(long userId, String previousStatus, LocalDateTime now) {
        jdbc.update("""
                UPDATE app_user
                   SET status = 'TRASHED', previous_status = ?,
                       trash_started_at = ?, deleted_at = ?,
                       row_version = row_version + 1
                 WHERE id = ? AND status IN ('ACTIVE', 'LIMITED')
                """, previousStatus, now, now, userId);
        jdbc.update("""
                UPDATE user_credential
                   SET disabled_at = ?
                 WHERE user_id = ? AND credential_type = 'PASSWORD' AND disabled_at IS NULL
                """, now, userId);
    }

    public long lockSessionVersion(long userId) {
        Long version = jdbc.queryForObject("""
                SELECT session_version
                  FROM app_user
                 WHERE id = ?
                 FOR UPDATE
                """, Long.class, userId);
        if (version == null) {
            throw new IllegalStateException("用户不存在，无法签发密码重置凭证");
        }
        return version;
    }

    public void revokeActivePasswordResetTokens(long userId) {
        jdbc.update("""
                UPDATE password_reset_token
                   SET status = 'REVOKED', row_version = row_version + 1
                 WHERE user_id = ? AND status = 'ACTIVE'
                """, userId);
    }

    public long insertPasswordResetToken(
            String externalId,
            long userId,
            String tokenHash,
            LocalDateTime expiresAt,
            String ipHash,
            long sessionVersion,
            LocalDateTime createdAt) {
        return insertAndReturnId("""
                INSERT INTO password_reset_token (
                    external_id, user_id, token_hash, status, expires_at,
                    consumed_at, request_ip_hash, session_version_at_issue, created_at, updated_at
                ) VALUES (?, ?, ?, 'ACTIVE', ?, NULL, ?, ?, ?, ?)
                """, externalId, userId, tokenHash, expiresAt, ipHash, sessionVersion,
                createdAt, createdAt);
    }

    public Optional<AuthModels.PasswordResetToken> findPasswordResetTokenForUpdate(
            String tokenHash,
            String normalizedEmail) {
        return jdbc.query("""
                SELECT t.id, t.user_id, t.status, t.expires_at,
                       t.session_version_at_issue, u.session_version AS current_session_version,
                       u.status AS user_status, c.password_hash
                  FROM password_reset_token t
                  JOIN app_user u ON u.id = t.user_id
                  JOIN user_credential c
                    ON c.user_id = u.id
                   AND c.credential_type = 'PASSWORD'
                   AND c.disabled_at IS NULL
                 WHERE t.token_hash = ? AND u.normalized_email = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new AuthModels.PasswordResetToken(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("status"),
                        rs.getString("user_status"),
                        rs.getString("password_hash"),
                        rs.getObject("expires_at", LocalDateTime.class),
                        rs.getLong("session_version_at_issue"),
                        rs.getLong("current_session_version")), tokenHash, normalizedEmail)
                .stream().findFirst();
    }

    public void expirePasswordResetToken(long id) {
        jdbc.update("""
                UPDATE password_reset_token
                   SET status = 'EXPIRED', row_version = row_version + 1
                 WHERE id = ? AND status = 'ACTIVE'
                """, id);
    }

    public void consumePasswordResetToken(long id, LocalDateTime consumedAt) {
        jdbc.update("""
                UPDATE password_reset_token
                   SET status = 'CONSUMED', consumed_at = ?, row_version = row_version + 1
                 WHERE id = ? AND status = 'ACTIVE'
                """, consumedAt, id);
    }

    public void updatePasswordCredential(
            long userId,
            String passwordHash,
            String hashPolicyKey,
            LocalDateTime changedAt) {
        jdbc.update("""
                UPDATE user_credential
                   SET password_hash = ?, hash_policy_key = ?, password_changed_at = ?,
                       compromised_checked_at = NULL
                 WHERE user_id = ?
                   AND credential_type = 'PASSWORD'
                   AND disabled_at IS NULL
                """, passwordHash, hashPolicyKey, changedAt, userId);
    }

    public void updateLastLogin(long userId, LocalDateTime now) {
        jdbc.update("""
                UPDATE app_user
                   SET last_login_at = ?, row_version = row_version + 1
                 WHERE id = ?
                """, now, userId);
    }

    public void expireElapsedSessions(long userId, LocalDateTime now) {
        jdbc.update("""
                UPDATE auth_session
                   SET status = 'EXPIRED', row_version = row_version + 1
                 WHERE user_id = ? AND status = 'ACTIVE'
                   AND (idle_expires_at <= ? OR absolute_expires_at <= ?)
                """, userId, now, now);
    }

    public List<Long> lockActiveSessionIds(long userId) {
        return jdbc.queryForList("""
                SELECT id
                  FROM auth_session
                 WHERE user_id = ? AND status = 'ACTIVE'
                 ORDER BY issued_at ASC, id ASC
                 FOR UPDATE
                """, Long.class, userId);
    }

    public void revokeSession(long sessionId, String reason, LocalDateTime now) {
        jdbc.update("""
                UPDATE auth_session
                   SET status = 'REVOKED', revoked_at = ?, revoke_reason = ?,
                       row_version = row_version + 1
                 WHERE id = ? AND status = 'ACTIVE'
                """, now, reason, sessionId);
    }

    public long insertSession(
            String externalId,
            long userId,
            long deviceId,
            String tokenHash,
            String csrfHash,
            String authLevel,
            String ipHash,
            String userAgentHash,
            LocalDateTime now,
            LocalDateTime idleExpiresAt,
            LocalDateTime absoluteExpiresAt) {
        return insertAndReturnId("""
                INSERT INTO auth_session (
                    external_id, user_id, device_id, token_hash, token_version,
                    status, auth_level, csrf_secret_hash, ip_prefix_hash,
                    user_agent_hash, issued_at, token_rotated_at, last_seen_at,
                    idle_expires_at, absolute_expires_at, step_up_verified_at,
                    revoked_at, revoke_reason
                ) VALUES (?, ?, ?, ?, 1, 'ACTIVE', ?, ?, ?, ?,
                          ?, ?, ?, ?, ?, NULL, NULL, NULL)
                """, externalId, userId, deviceId, tokenHash, authLevel, csrfHash,
                ipHash, userAgentHash,
                now, now, now, idleExpiresAt, absoluteExpiresAt);
    }

    public Optional<AuthModels.SessionRecord> findSessionByTokenHash(String tokenHash) {
        return jdbc.query("""
                SELECT s.id, s.external_id, s.user_id, s.auth_level,
                       s.csrf_secret_hash, s.token_rotated_at, s.last_seen_at,
                       s.idle_expires_at, s.absolute_expires_at, s.row_version,
                       u.external_id AS user_external_id, u.email_display,
                       u.status AS user_status, p.display_name
                  FROM auth_session s
                  JOIN app_user u ON u.id = s.user_id
                  JOIN user_profile p ON p.user_id = u.id
                 WHERE s.token_hash = ? AND s.status = 'ACTIVE'
                """, (rs, rowNum) -> new AuthModels.SessionRecord(
                        rs.getLong("id"),
                        rs.getString("external_id"),
                        rs.getLong("user_id"),
                        rs.getString("user_external_id"),
                        rs.getString("email_display"),
                        rs.getString("user_status"),
                        rs.getString("display_name"),
                        rs.getString("auth_level"),
                        rs.getString("csrf_secret_hash"),
                        rs.getObject("token_rotated_at", LocalDateTime.class),
                        rs.getObject("last_seen_at", LocalDateTime.class),
                        rs.getObject("idle_expires_at", LocalDateTime.class),
                        rs.getObject("absolute_expires_at", LocalDateTime.class),
                        rs.getLong("row_version")), tokenHash).stream().findFirst();
    }

    public boolean rotateSession(
            long id,
            long rowVersion,
            String tokenHash,
            String csrfHash,
            LocalDateTime now,
            LocalDateTime idleExpiresAt) {
        return jdbc.update("""
                UPDATE auth_session
                   SET token_hash = ?, csrf_secret_hash = ?,
                       token_version = token_version + 1,
                       token_rotated_at = ?, last_seen_at = ?, idle_expires_at = ?,
                       row_version = row_version + 1
                 WHERE id = ? AND row_version = ? AND status = 'ACTIVE'
                """, tokenHash, csrfHash, now, now, idleExpiresAt, id, rowVersion) == 1;
    }

    public void touchSession(long id, LocalDateTime now, LocalDateTime idleExpiresAt) {
        jdbc.update("""
                UPDATE auth_session
                   SET last_seen_at = ?, idle_expires_at = ?, row_version = row_version + 1
                 WHERE id = ? AND status = 'ACTIVE'
                """, now, idleExpiresAt, id);
    }

    public void markSessionExpired(long id) {
        jdbc.update("""
                UPDATE auth_session
                   SET status = 'EXPIRED', row_version = row_version + 1
                 WHERE id = ? AND status = 'ACTIVE'
                """, id);
    }

    public boolean rotateCsrf(long id, String csrfHash) {
        return jdbc.update("""
                UPDATE auth_session
                   SET csrf_secret_hash = ?, row_version = row_version + 1
                 WHERE id = ? AND status = 'ACTIVE'
                """, csrfHash, id) == 1;
    }

    public void revokeAllSessions(long userId, String reason, LocalDateTime now) {
        jdbc.update("""
                UPDATE auth_session
                   SET status = 'REVOKED', revoked_at = ?, revoke_reason = ?,
                       row_version = row_version + 1
                 WHERE user_id = ? AND status = 'ACTIVE'
                """, now, reason, userId);
        jdbc.update("""
                UPDATE app_user
                   SET session_version = session_version + 1, row_version = row_version + 1
                 WHERE id = ?
                """, userId);
    }

    public void insertSecurityEvent(
            String externalId,
            Long userId,
            Long sessionId,
            String type,
            String severity,
            String emailHash,
            String ipHash,
            LocalDateTime now) {
        jdbc.update("""
                INSERT INTO security_event (
                    external_id, user_id, event_type, severity,
                    normalized_email_hash, device_id, session_id,
                    ip_prefix_hash, risk_score, metadata_json, occurred_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, NULL, JSON_OBJECT(), ?)
                """, externalId, userId, type, severity, emailHash, sessionId, ipHash, now);
    }

    private Optional<AuthModels.LegalDocumentVersion> findActiveLegalDocumentVersionForUpdate(
            String requestedTable,
            String versionKey,
            String locale,
            LocalDateTime currentTime) {
        String table = switch (requestedTable) {
            case "terms_document_version" -> "terms_document_version";
            case "privacy_notice_version" -> "privacy_notice_version";
            default -> throw new IllegalArgumentException("不支持的法律文档表");
        };
        String sql = """
                SELECT id, version_key, content_hash, content_url
                  FROM %s
                 WHERE version_key = ?
                   AND locale = ?
                   AND status = 'ACTIVE'
                   AND effective_at <= ?
                 FOR UPDATE
                """.formatted(table);
        return jdbc.query(sql, (rs, rowNum) -> new AuthModels.LegalDocumentVersion(
                        rs.getLong("id"),
                        rs.getString("version_key"),
                        rs.getString("content_hash"),
                        rs.getString("content_url")), versionKey, locale, currentTime)
                .stream().findFirst();
    }

    private long insertAndReturnId(String sql, Object... arguments) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < arguments.length; index++) {
                Object value = arguments[index];
                if (value instanceof LocalDateTime dateTime) {
                    statement.setObject(index + 1, dateTime);
                } else {
                    statement.setObject(index + 1, value);
                }
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("数据库没有返回新增记录 ID");
        }
        return key.longValue();
    }
}
