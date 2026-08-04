-- ExamInsight V2 identity, authentication and security records.
-- This migration creates structure only. It never inserts users, administrators,
-- passwords, verification codes, session tokens, recovery codes or test data.

CREATE TABLE app_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    email_display VARCHAR(254) NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    age_gate_acknowledged_at DATETIME(3) NOT NULL,
    email_verified_at DATETIME(3) NULL,
    last_login_at DATETIME(3) NULL,
    session_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    trash_started_at DATETIME(3) NULL,
    previous_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    deleted_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user__external_id UNIQUE (external_id),
    CONSTRAINT uq_app_user__normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_app_user__status CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LIMITED', 'DELETION_PENDING', 'TRASHED', 'PURGED')
    ),
    CONSTRAINT ck_app_user__session_version CHECK (session_version > 0),
    CONSTRAINT ck_app_user__previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('ACTIVE', 'LIMITED')
    ),
    CONSTRAINT ck_app_user__lifecycle CHECK (
        (
            status = 'PENDING_VERIFICATION'
            AND email_verified_at IS NULL
            AND trash_started_at IS NULL
            AND deleted_at IS NULL
            AND previous_status IS NULL
        )
        OR (
            status IN ('ACTIVE', 'LIMITED')
            AND email_verified_at IS NOT NULL
            AND trash_started_at IS NULL
            AND deleted_at IS NULL
            AND previous_status IS NULL
        )
        OR (
            status = 'DELETION_PENDING'
            AND email_verified_at IS NOT NULL
            AND trash_started_at IS NULL
            AND deleted_at IS NULL
            AND previous_status IN ('ACTIVE', 'LIMITED')
        )
        OR (
            status IN ('TRASHED', 'PURGED')
            AND email_verified_at IS NOT NULL
            AND trash_started_at IS NOT NULL
            AND deleted_at IS NOT NULL
            AND previous_status IN ('ACTIVE', 'LIMITED')
            AND deleted_at >= trash_started_at
        )
    ),
    INDEX idx_app_user__status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_credential (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    credential_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hash_policy_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_changed_at DATETIME(3) NOT NULL,
    compromised_checked_at DATETIME(3) NULL,
    disabled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_user_credential PRIMARY KEY (id),
    CONSTRAINT uq_user_credential__external_id UNIQUE (external_id),
    CONSTRAINT uq_user_credential__user_type UNIQUE (user_id, credential_type),
    CONSTRAINT fk_user_credential__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_credential__type CHECK (credential_type = 'PASSWORD'),
    CONSTRAINT ck_user_credential__password_hash CHECK (
        CHAR_LENGTH(TRIM(password_hash)) > 0
    ),
    CONSTRAINT ck_user_credential__hash_policy CHECK (
        CHAR_LENGTH(TRIM(hash_policy_key)) > 0
    ),
    CONSTRAINT ck_user_credential__security_times CHECK (
        (compromised_checked_at IS NULL OR compromised_checked_at >= password_changed_at)
        AND (disabled_at IS NULL OR disabled_at >= password_changed_at)
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    avatar_asset_id BIGINT UNSIGNED NULL,
    bio VARCHAR(300) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_user_profile PRIMARY KEY (id),
    CONSTRAINT uq_user_profile__external_id UNIQUE (external_id),
    CONSTRAINT uq_user_profile__user_id UNIQUE (user_id),
    CONSTRAINT fk_user_profile__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profile__display_name CHECK (
        CHAR_LENGTH(TRIM(display_name)) > 0
    ),
    INDEX idx_user_profile__avatar_asset_id (avatar_asset_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_setting (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    theme VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SYSTEM',
    locale VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    timezone VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reduced_motion BOOLEAN NOT NULL DEFAULT FALSE,
    email_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    learning_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_user_setting PRIMARY KEY (id),
    CONSTRAINT uq_user_setting__external_id UNIQUE (external_id),
    CONSTRAINT uq_user_setting__user_id UNIQUE (user_id),
    CONSTRAINT fk_user_setting__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_setting__theme CHECK (
        theme IN ('SYSTEM', 'LIGHT', 'DARK')
    ),
    CONSTRAINT ck_user_setting__locale CHECK (
        CHAR_LENGTH(TRIM(locale)) > 0
    ),
    CONSTRAINT ck_user_setting__timezone CHECK (
        CHAR_LENGTH(TRIM(timezone)) > 0
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_device (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    device_fingerprint_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(120) NULL,
    first_seen_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    trust_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    risk_level VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revoked_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_user_device PRIMARY KEY (id),
    CONSTRAINT uq_user_device__external_id UNIQUE (external_id),
    CONSTRAINT uq_user_device__user_fingerprint UNIQUE (user_id, device_fingerprint_hash),
    CONSTRAINT fk_user_device__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_device__trust_status CHECK (
        trust_status IN ('UNVERIFIED', 'TRUSTED', 'REVOKED')
    ),
    CONSTRAINT ck_user_device__risk_level CHECK (
        risk_level IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    CONSTRAINT ck_user_device__seen_times CHECK (last_seen_at >= first_seen_at),
    CONSTRAINT ck_user_device__revocation CHECK (
        (trust_status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_at >= first_seen_at)
        OR (trust_status <> 'REVOKED' AND revoked_at IS NULL)
    ),
    INDEX idx_user_device__user_last_seen (user_id, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auth_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_version INT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    auth_level VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    csrf_secret_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ip_prefix_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    issued_at DATETIME(3) NOT NULL,
    token_rotated_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    idle_expires_at DATETIME(3) NOT NULL,
    absolute_expires_at DATETIME(3) NOT NULL,
    step_up_verified_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT uq_auth_session__external_id UNIQUE (external_id),
    CONSTRAINT uq_auth_session__token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_session__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_session__device_id__user_device
        FOREIGN KEY (device_id) REFERENCES user_device (id) ON DELETE SET NULL,
    CONSTRAINT ck_auth_session__token_version CHECK (token_version > 0),
    CONSTRAINT ck_auth_session__status CHECK (
        status IN ('ACTIVE', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT ck_auth_session__auth_level CHECK (
        auth_level IN ('PRIMARY', 'STEP_UP')
    ),
    CONSTRAINT ck_auth_session__auth_level_time CHECK (
        (auth_level = 'PRIMARY' AND step_up_verified_at IS NULL)
        OR (auth_level = 'STEP_UP' AND step_up_verified_at IS NOT NULL)
    ),
    CONSTRAINT ck_auth_session__session_times CHECK (
        token_rotated_at >= issued_at
        AND last_seen_at >= token_rotated_at
        AND idle_expires_at > last_seen_at
        AND absolute_expires_at >= idle_expires_at
        AND (step_up_verified_at IS NULL OR step_up_verified_at BETWEEN issued_at AND last_seen_at)
    ),
    CONSTRAINT ck_auth_session__revocation CHECK (
        (
            status = 'REVOKED'
            AND revoked_at IS NOT NULL
            AND revoke_reason IS NOT NULL
            AND revoked_at >= issued_at
        )
        OR (
            status IN ('ACTIVE', 'EXPIRED')
            AND revoked_at IS NULL
            AND revoke_reason IS NULL
        )
    ),
    INDEX idx_auth_session__user_status_expiry (user_id, status, absolute_expires_at),
    INDEX idx_auth_session__device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE email_verification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    normalized_email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    purpose VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    verification_proof_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    verified_at DATETIME(3) NULL,
    proof_expires_at DATETIME(3) NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 5,
    consumed_at DATETIME(3) NULL,
    request_ip_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    device_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    active_email_key VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('PENDING', 'VERIFIED') THEN normalized_email ELSE NULL END
        ) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_email_verification PRIMARY KEY (id),
    CONSTRAINT uq_email_verification__external_id UNIQUE (external_id),
    CONSTRAINT uq_email_verification__active_email_purpose UNIQUE (active_email_key, purpose),
    CONSTRAINT fk_email_verification__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_email_verification__purpose CHECK (
        purpose IN ('REGISTRATION', 'LOGIN_STEP_UP')
    ),
    CONSTRAINT ck_email_verification__status CHECK (
        status IN ('PENDING', 'VERIFIED', 'CONSUMED', 'EXPIRED', 'LOCKED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_email_verification__attempts CHECK (
        max_attempts > 0
        AND attempt_count <= max_attempts
        AND (status <> 'LOCKED' OR attempt_count = max_attempts)
    ),
    CONSTRAINT ck_email_verification__proof_fields CHECK (
        (
            verification_proof_hash IS NULL
            AND verified_at IS NULL
            AND proof_expires_at IS NULL
        )
        OR (
            verification_proof_hash IS NOT NULL
            AND verified_at IS NOT NULL
            AND proof_expires_at IS NOT NULL
            AND verified_at <= expires_at
            AND proof_expires_at > verified_at
        )
    ),
    CONSTRAINT ck_email_verification__lifecycle CHECK (
        expires_at > created_at
        AND (
            (
                status = 'PENDING'
                AND verification_proof_hash IS NULL
                AND consumed_at IS NULL
            )
            OR (
                status = 'VERIFIED'
                AND verification_proof_hash IS NOT NULL
                AND consumed_at IS NULL
            )
            OR (
                status = 'CONSUMED'
                AND verification_proof_hash IS NOT NULL
                AND consumed_at IS NOT NULL
                AND consumed_at BETWEEN verified_at AND proof_expires_at
            )
            OR (status = 'EXPIRED' AND consumed_at IS NULL)
            OR (
                status IN ('LOCKED', 'SUPERSEDED')
                AND verification_proof_hash IS NULL
                AND consumed_at IS NULL
            )
        )
    ),
    INDEX idx_email_verification__email_purpose_created (normalized_email, purpose, created_at),
    INDEX idx_email_verification__status_expiry (status, expires_at),
    INDEX idx_email_verification__user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE email_delivery (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    verification_id BIGINT UNSIGNED NULL,
    user_id BIGINT UNSIGNED NULL,
    template_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_message_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    sent_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    failed_at DATETIME(3) NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_email_delivery PRIMARY KEY (id),
    CONSTRAINT uq_email_delivery__external_id UNIQUE (external_id),
    CONSTRAINT fk_email_delivery__verification_id__email_verification
        FOREIGN KEY (verification_id) REFERENCES email_verification (id) ON DELETE SET NULL,
    CONSTRAINT fk_email_delivery__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_email_delivery__status CHECK (
        status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED')
    ),
    CONSTRAINT ck_email_delivery__attempt_count CHECK (
        status = 'QUEUED' OR attempt_count > 0
    ),
    CONSTRAINT ck_email_delivery__lifecycle CHECK (
        (
            status = 'QUEUED'
            AND sent_at IS NULL
            AND delivered_at IS NULL
            AND failed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status = 'SENT'
            AND sent_at IS NOT NULL
            AND provider_message_id IS NOT NULL
            AND delivered_at IS NULL
            AND failed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status = 'DELIVERED'
            AND sent_at IS NOT NULL
            AND provider_message_id IS NOT NULL
            AND delivered_at IS NOT NULL
            AND delivered_at >= sent_at
            AND failed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status = 'FAILED'
            AND delivered_at IS NULL
            AND failed_at IS NOT NULL
            AND failure_code IS NOT NULL
            AND (sent_at IS NULL OR failed_at >= sent_at)
        )
        OR (
            status = 'BOUNCED'
            AND sent_at IS NOT NULL
            AND provider_message_id IS NOT NULL
            AND delivered_at IS NULL
            AND failed_at IS NOT NULL
            AND failed_at >= sent_at
            AND failure_code IS NOT NULL
        )
    ),
    INDEX idx_email_delivery__status_created (status, created_at),
    INDEX idx_email_delivery__provider_message (provider_key, provider_message_id),
    INDEX idx_email_delivery__verification_id (verification_id),
    INDEX idx_email_delivery__user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_token (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    request_ip_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    session_version_at_issue BIGINT UNSIGNED NOT NULL,
    active_slot TINYINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_token__external_id UNIQUE (external_id),
    CONSTRAINT uq_password_reset_token__token_hash UNIQUE (token_hash),
    CONSTRAINT uq_password_reset_token__user_active_slot UNIQUE (user_id, active_slot),
    CONSTRAINT fk_password_reset_token__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_password_reset_token__status CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT ck_password_reset_token__session_version CHECK (
        session_version_at_issue > 0
    ),
    CONSTRAINT ck_password_reset_token__lifecycle CHECK (
        expires_at > created_at
        AND (
            (status = 'ACTIVE' AND consumed_at IS NULL)
            OR (
                status = 'CONSUMED'
                AND consumed_at IS NOT NULL
                AND consumed_at BETWEEN created_at AND expires_at
            )
            OR (status IN ('REVOKED', 'EXPIRED') AND consumed_at IS NULL)
        )
    ),
    INDEX idx_password_reset_token__user_status_expiry (user_id, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE security_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    severity VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_email_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    device_id BIGINT UNSIGNED NULL,
    session_id BIGINT UNSIGNED NULL,
    ip_prefix_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    risk_score DECIMAL(7,6) NULL,
    metadata_json JSON NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_security_event PRIMARY KEY (id),
    CONSTRAINT uq_security_event__external_id UNIQUE (external_id),
    CONSTRAINT fk_security_event__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_security_event__device_id__user_device
        FOREIGN KEY (device_id) REFERENCES user_device (id) ON DELETE SET NULL,
    CONSTRAINT fk_security_event__session_id__auth_session
        FOREIGN KEY (session_id) REFERENCES auth_session (id) ON DELETE SET NULL,
    CONSTRAINT ck_security_event__event_type CHECK (
        CHAR_LENGTH(TRIM(event_type)) > 0
    ),
    CONSTRAINT ck_security_event__severity CHECK (
        severity IN ('INFO', 'WARN', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_security_event__risk_score CHECK (
        risk_score IS NULL OR risk_score BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_security_event__metadata_object CHECK (
        metadata_json IS NULL OR JSON_TYPE(metadata_json) = 'OBJECT'
    ),
    INDEX idx_security_event__user_time (user_id, occurred_at),
    INDEX idx_security_event__event_time (event_type, occurred_at),
    INDEX idx_security_event__severity_time (severity, occurred_at),
    INDEX idx_security_event__device_id (device_id),
    INDEX idx_security_event__session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE admin_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_login_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_admin_user PRIMARY KEY (id),
    CONSTRAINT uq_admin_user__external_id UNIQUE (external_id),
    CONSTRAINT uq_admin_user__normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_admin_user__status CHECK (
        status IN ('INVITED', 'ACTIVE', 'DISABLED')
    ),
    CONSTRAINT ck_admin_user__display_name CHECK (
        CHAR_LENGTH(TRIM(display_name)) > 0
    ),
    INDEX idx_admin_user__status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE admin_mfa_credential (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admin_user_id BIGINT UNSIGNED NOT NULL,
    credential_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_id VARBINARY(512) NULL,
    secret_ciphertext VARBINARY(1024) NULL,
    public_key LONGTEXT NULL,
    sign_count BIGINT UNSIGNED NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_used_at DATETIME(3) NULL,
    active_totp_slot TINYINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN credential_type = 'TOTP' AND status = 'ACTIVE' THEN 1 ELSE NULL END
        ) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_admin_mfa_credential PRIMARY KEY (id),
    CONSTRAINT uq_admin_mfa_credential__external_id UNIQUE (external_id),
    CONSTRAINT uq_admin_mfa_credential__type_credential UNIQUE (credential_type, credential_id),
    CONSTRAINT uq_admin_mfa_credential__admin_active_totp UNIQUE (admin_user_id, active_totp_slot),
    CONSTRAINT fk_admin_mfa_credential__admin_id__admin_user
        FOREIGN KEY (admin_user_id) REFERENCES admin_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_admin_mfa_credential__type CHECK (
        credential_type IN ('PASSKEY', 'TOTP')
    ),
    CONSTRAINT ck_admin_mfa_credential__status CHECK (
        status IN ('ACTIVE', 'REVOKED')
    ),
    CONSTRAINT ck_admin_mfa_credential__shape CHECK (
        (
            credential_type = 'PASSKEY'
            AND credential_id IS NOT NULL
            AND OCTET_LENGTH(credential_id) > 0
            AND secret_ciphertext IS NULL
            AND public_key IS NOT NULL
            AND CHAR_LENGTH(TRIM(public_key)) > 0
            AND sign_count IS NOT NULL
        )
        OR (
            credential_type = 'TOTP'
            AND credential_id IS NULL
            AND secret_ciphertext IS NOT NULL
            AND OCTET_LENGTH(secret_ciphertext) > 0
            AND public_key IS NULL
            AND sign_count IS NULL
        )
    ),
    CONSTRAINT ck_admin_mfa_credential__last_used CHECK (
        last_used_at IS NULL OR last_used_at >= created_at
    ),
    INDEX idx_admin_mfa_credential__admin_status (admin_user_id, status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE admin_recovery_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admin_user_id BIGINT UNSIGNED NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    used_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_admin_recovery_code PRIMARY KEY (id),
    CONSTRAINT uq_admin_recovery_code__external_id UNIQUE (external_id),
    CONSTRAINT uq_admin_recovery_code__admin_code UNIQUE (admin_user_id, code_hash),
    CONSTRAINT fk_admin_recovery_code__admin_id__admin_user
        FOREIGN KEY (admin_user_id) REFERENCES admin_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_admin_recovery_code__used_time CHECK (
        used_at IS NULL OR used_at >= created_at
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE admin_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admin_user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_version INT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    csrf_secret_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mfa_verified_at DATETIME(3) NOT NULL,
    step_up_verified_at DATETIME(3) NULL,
    issued_at DATETIME(3) NOT NULL,
    token_rotated_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    idle_expires_at DATETIME(3) NOT NULL,
    absolute_expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    ip_prefix_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_admin_session PRIMARY KEY (id),
    CONSTRAINT uq_admin_session__external_id UNIQUE (external_id),
    CONSTRAINT uq_admin_session__token_hash UNIQUE (token_hash),
    CONSTRAINT fk_admin_session__admin_id__admin_user
        FOREIGN KEY (admin_user_id) REFERENCES admin_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_admin_session__token_version CHECK (token_version > 0),
    CONSTRAINT ck_admin_session__status CHECK (
        status IN ('ACTIVE', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT ck_admin_session__session_times CHECK (
        mfa_verified_at <= issued_at
        AND token_rotated_at >= issued_at
        AND last_seen_at >= token_rotated_at
        AND idle_expires_at > last_seen_at
        AND absolute_expires_at >= idle_expires_at
        AND (step_up_verified_at IS NULL OR step_up_verified_at BETWEEN mfa_verified_at AND last_seen_at)
    ),
    CONSTRAINT ck_admin_session__revocation CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_at >= issued_at)
        OR (status IN ('ACTIVE', 'EXPIRED') AND revoked_at IS NULL)
    ),
    INDEX idx_admin_session__admin_status_expiry (admin_user_id, status, absolute_expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE async_job
    ADD CONSTRAINT fk_async_job__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL;

ALTER TABLE idempotency_record
    ADD CONSTRAINT fk_idempotency_record__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL;

ALTER TABLE domain_audit_event
    ADD CONSTRAINT fk_domain_audit_event__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL;
