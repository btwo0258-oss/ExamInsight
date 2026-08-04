-- ExamInsight V2 privacy rights, export, deletion, retention and controlled
-- administrator access records. This migration creates structure only.

CREATE TABLE processing_purpose (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    legal_basis VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_processing_purpose PRIMARY KEY (id),
    CONSTRAINT uq_processing_purpose__external_id UNIQUE (external_id),
    CONSTRAINT uq_processing_purpose__purpose_key UNIQUE (purpose_key),
    CONSTRAINT ck_processing_purpose__purpose_key CHECK (
        CHAR_LENGTH(TRIM(purpose_key)) > 0
    ),
    CONSTRAINT ck_processing_purpose__display_name CHECK (
        CHAR_LENGTH(TRIM(display_name)) > 0
    ),
    CONSTRAINT ck_processing_purpose__legal_basis CHECK (
        CHAR_LENGTH(TRIM(legal_basis)) > 0
    ),
    CONSTRAINT ck_processing_purpose__required CHECK (
        required IN (FALSE, TRUE) AND NOT (required = TRUE AND legal_basis = 'CONSENT')
    ),
    CONSTRAINT ck_processing_purpose__status CHECK (
        status IN ('ACTIVE', 'DISABLED')
    ),
    INDEX idx_processing_purpose__status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE privacy_notice_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    locale VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_url VARCHAR(500) NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    retired_at DATETIME(3) NULL,
    active_locale_key VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (IF(status = 'ACTIVE', locale, NULL)) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_privacy_notice_version PRIMARY KEY (id),
    CONSTRAINT uq_privacy_notice_version__external_id UNIQUE (external_id),
    CONSTRAINT uq_privacy_notice_version__key_locale UNIQUE (version_key, locale),
    CONSTRAINT uq_privacy_notice_version__active_locale UNIQUE (active_locale_key),
    CONSTRAINT ck_privacy_notice_version__version_key CHECK (
        CHAR_LENGTH(TRIM(version_key)) > 0
    ),
    CONSTRAINT ck_privacy_notice_version__locale CHECK (
        CHAR_LENGTH(TRIM(locale)) > 0
    ),
    CONSTRAINT ck_privacy_notice_version__content_hash CHECK (
        content_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_privacy_notice_version__content_url CHECK (
        CHAR_LENGTH(TRIM(content_url)) > 0
        AND (content_url LIKE 'https://%' OR content_url LIKE '/%')
    ),
    CONSTRAINT ck_privacy_notice_version__status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_privacy_notice_version__lifecycle CHECK (
        (status IN ('DRAFT', 'ACTIVE') AND retired_at IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= effective_at)
    ),
    INDEX idx_privacy_notice_version__status_effective (status, effective_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE privacy_notice_acknowledgement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    notice_version_id BIGINT UNSIGNED NOT NULL,
    acknowledged_at DATETIME(3) NOT NULL,
    ip_prefix_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_privacy_notice_acknowledgement PRIMARY KEY (id),
    CONSTRAINT uq_privacy_notice_acknowledgement__external_id UNIQUE (external_id),
    CONSTRAINT uq_privacy_notice_acknowledgement__user_notice UNIQUE (user_id, notice_version_id),
    CONSTRAINT fk_privacy_notice_ack__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_privacy_notice_ack__notice_id__notice_version
        FOREIGN KEY (notice_version_id) REFERENCES privacy_notice_version (id),
    CONSTRAINT ck_privacy_notice_ack__ip_hash CHECK (
        ip_prefix_hash IS NULL OR ip_prefix_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_privacy_notice_ack__user_agent_hash CHECK (
        user_agent_hash IS NULL OR user_agent_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_privacy_notice_ack__acknowledged_time CHECK (
        acknowledged_at >= created_at
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_consent (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    purpose_id BIGINT UNSIGNED NOT NULL,
    notice_version_id BIGINT UNSIGNED NULL,
    decision VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_user_consent PRIMARY KEY (id),
    CONSTRAINT uq_user_consent__external_id UNIQUE (external_id),
    CONSTRAINT fk_user_consent__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_consent__purpose_id__processing_purpose
        FOREIGN KEY (purpose_id) REFERENCES processing_purpose (id),
    CONSTRAINT fk_user_consent__notice_id__notice_version
        FOREIGN KEY (notice_version_id) REFERENCES privacy_notice_version (id),
    CONSTRAINT ck_user_consent__decision CHECK (
        decision IN ('GRANTED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_user_consent__source CHECK (
        source IN ('ONBOARDING', 'SETTINGS', 'PRIVACY_REQUEST')
    ),
    CONSTRAINT ck_user_consent__notice_required CHECK (
        decision <> 'GRANTED' OR notice_version_id IS NOT NULL
    ),
    CONSTRAINT ck_user_consent__occurred_time CHECK (
        occurred_at >= created_at
    ),
    INDEX idx_user_consent__user_purpose_time (user_id, purpose_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE processor (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    processor_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    service_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_processor PRIMARY KEY (id),
    CONSTRAINT uq_processor__external_id UNIQUE (external_id),
    CONSTRAINT uq_processor__processor_key UNIQUE (processor_key),
    CONSTRAINT ck_processor__processor_key CHECK (
        CHAR_LENGTH(TRIM(processor_key)) > 0
    ),
    CONSTRAINT ck_processor__display_name CHECK (
        CHAR_LENGTH(TRIM(display_name)) > 0
    ),
    CONSTRAINT ck_processor__service_type CHECK (
        CHAR_LENGTH(TRIM(service_type)) > 0
    ),
    CONSTRAINT ck_processor__status CHECK (
        status IN ('ACTIVE', 'DISABLED')
    ),
    INDEX idx_processor__status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE processor_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    processor_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    region VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    data_categories_json JSON NOT NULL,
    purpose_keys_json JSON NOT NULL,
    retention_summary VARCHAR(1000) NULL,
    terms_url VARCHAR(500) NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    retired_at DATETIME(3) NULL,
    active_processor_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (IF(status = 'ACTIVE', processor_id, NULL)) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_processor_version PRIMARY KEY (id),
    CONSTRAINT uq_processor_version__external_id UNIQUE (external_id),
    CONSTRAINT uq_processor_version__processor_version UNIQUE (processor_id, version_no),
    CONSTRAINT uq_processor_version__active_processor UNIQUE (active_processor_id),
    CONSTRAINT fk_processor_version__processor_id__processor
        FOREIGN KEY (processor_id) REFERENCES processor (id),
    CONSTRAINT ck_processor_version__version_no CHECK (
        version_no > 0
    ),
    CONSTRAINT ck_processor_version__region CHECK (
        region IS NULL OR CHAR_LENGTH(TRIM(region)) > 0
    ),
    CONSTRAINT ck_processor_version__data_categories CHECK (
        JSON_TYPE(data_categories_json) = 'ARRAY'
    ),
    CONSTRAINT ck_processor_version__purpose_keys CHECK (
        JSON_TYPE(purpose_keys_json) = 'ARRAY'
    ),
    CONSTRAINT ck_processor_version__retention_summary CHECK (
        retention_summary IS NULL OR CHAR_LENGTH(TRIM(retention_summary)) > 0
    ),
    CONSTRAINT ck_processor_version__terms_url CHECK (
        terms_url IS NULL
        OR (CHAR_LENGTH(TRIM(terms_url)) > 0 AND terms_url LIKE 'https://%')
    ),
    CONSTRAINT ck_processor_version__status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_processor_version__lifecycle CHECK (
        (status IN ('DRAFT', 'ACTIVE') AND retired_at IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= effective_at)
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE privacy_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description VARCHAR(1000) NULL,
    verified_at DATETIME(3) NULL,
    due_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    rejection_reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    active_request_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            IF(status IN ('RECEIVED', 'VERIFYING', 'IN_PROGRESS'), request_type, NULL)
        ) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_privacy_request PRIMARY KEY (id),
    CONSTRAINT uq_privacy_request__external_id UNIQUE (external_id),
    CONSTRAINT uq_privacy_request__user_active_type UNIQUE (user_id, active_request_type),
    CONSTRAINT fk_privacy_request__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_privacy_request__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_privacy_request__type CHECK (
        request_type IN ('ACCESS', 'EXPORT', 'CORRECTION', 'DELETION', 'RESTRICTION', 'OBJECTION')
    ),
    CONSTRAINT ck_privacy_request__status CHECK (
        status IN ('RECEIVED', 'VERIFYING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_privacy_request__description CHECK (
        description IS NULL OR CHAR_LENGTH(TRIM(description)) > 0
    ),
    CONSTRAINT ck_privacy_request__deadline CHECK (
        due_at > created_at
    ),
    CONSTRAINT ck_privacy_request__verified_time CHECK (
        verified_at IS NULL OR verified_at >= created_at
    ),
    CONSTRAINT ck_privacy_request__lifecycle CHECK (
        (
            status IN ('RECEIVED', 'VERIFYING')
            AND completed_at IS NULL
            AND rejection_reason_code IS NULL
        )
        OR (
            status = 'IN_PROGRESS'
            AND verified_at IS NOT NULL
            AND completed_at IS NULL
            AND rejection_reason_code IS NULL
        )
        OR (
            status IN ('COMPLETED', 'CANCELLED')
            AND completed_at IS NOT NULL
            AND completed_at >= created_at
            AND rejection_reason_code IS NULL
        )
        OR (
            status = 'REJECTED'
            AND completed_at IS NOT NULL
            AND completed_at >= created_at
            AND rejection_reason_code IS NOT NULL
        )
    ),
    INDEX idx_privacy_request__user_status_created (user_id, status, created_at),
    INDEX idx_privacy_request__subject_created (subject_hash, created_at),
    INDEX idx_privacy_request__status_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE privacy_request_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    privacy_request_id BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    safe_note VARCHAR(1000) NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_privacy_request_event PRIMARY KEY (id),
    CONSTRAINT uq_privacy_request_event__external_id UNIQUE (external_id),
    CONSTRAINT fk_privacy_request_event__request_id__privacy_request
        FOREIGN KEY (privacy_request_id) REFERENCES privacy_request (id) ON DELETE CASCADE,
    CONSTRAINT ck_privacy_request_event__actor_type CHECK (
        actor_type IN ('USER', 'ADMIN', 'SYSTEM', 'WORKER')
    ),
    CONSTRAINT ck_privacy_request_event__actor_ref CHECK (
        (actor_type = 'SYSTEM' AND actor_external_id IS NULL)
        OR (actor_type <> 'SYSTEM' AND actor_external_id IS NOT NULL)
    ),
    CONSTRAINT ck_privacy_request_event__event_type CHECK (
        CHAR_LENGTH(TRIM(event_type)) > 0
    ),
    CONSTRAINT ck_privacy_request_event__safe_note CHECK (
        safe_note IS NULL OR CHAR_LENGTH(TRIM(safe_note)) > 0
    ),
    CONSTRAINT ck_privacy_request_event__occurred_time CHECK (
        occurred_at >= created_at
    ),
    INDEX idx_privacy_request_event__request_time (privacy_request_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE data_export_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    privacy_request_id BIGINT UNSIGNED NULL,
    async_job_id BIGINT UNSIGNED NOT NULL,
    scope_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    package_storage_object_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    download_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    download_expires_at DATETIME(3) NULL,
    package_expires_at DATETIME(3) NULL,
    download_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_downloaded_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    active_export_slot TINYINT UNSIGNED
        GENERATED ALWAYS AS (IF(status IN ('QUEUED', 'RUNNING'), 1, NULL)) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_data_export_job PRIMARY KEY (id),
    CONSTRAINT uq_data_export_job__external_id UNIQUE (external_id),
    CONSTRAINT uq_data_export_job__async_job_id UNIQUE (async_job_id),
    CONSTRAINT uq_data_export_job__user_active UNIQUE (user_id, active_export_slot),
    CONSTRAINT fk_data_export_job__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_data_export_job__privacy_request_id__privacy_request
        FOREIGN KEY (privacy_request_id) REFERENCES privacy_request (id) ON DELETE SET NULL,
    CONSTRAINT fk_data_export_job__async_job_id__async_job
        FOREIGN KEY (async_job_id) REFERENCES async_job (id),
    CONSTRAINT fk_data_export_job__package_id__storage_object
        FOREIGN KEY (package_storage_object_id) REFERENCES storage_object (id) ON DELETE SET NULL,
    CONSTRAINT ck_data_export_job__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_data_export_job__scope CHECK (
        scope_key = 'ACCOUNT_ALL'
    ),
    CONSTRAINT ck_data_export_job__status CHECK (
        status IN ('QUEUED', 'RUNNING', 'READY', 'EXPIRED', 'FAILED', 'CANCELLED', 'PURGED')
    ),
    CONSTRAINT ck_data_export_job__download_hash CHECK (
        download_token_hash IS NULL OR download_token_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_data_export_job__download_pair CHECK (
        (download_token_hash IS NULL AND download_expires_at IS NULL)
        OR (download_token_hash IS NOT NULL AND download_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_data_export_job__download_count CHECK (
        (download_count = 0 AND last_downloaded_at IS NULL)
        OR (download_count > 0 AND last_downloaded_at IS NOT NULL)
    ),
    CONSTRAINT ck_data_export_job__lifecycle CHECK (
        (
            status IN ('QUEUED', 'RUNNING')
            AND download_token_hash IS NULL
            AND package_expires_at IS NULL
            AND download_count = 0
            AND completed_at IS NULL
        )
        OR (
            status = 'READY'
            AND package_expires_at IS NOT NULL
            AND completed_at IS NOT NULL
            AND package_expires_at > completed_at
            AND (download_expires_at IS NULL OR download_expires_at <= package_expires_at)
            AND (last_downloaded_at IS NULL OR last_downloaded_at >= completed_at)
        )
        OR (
            status IN ('EXPIRED', 'PURGED')
            AND download_token_hash IS NULL
            AND package_expires_at IS NOT NULL
            AND completed_at IS NOT NULL
        )
        OR (
            status IN ('FAILED', 'CANCELLED')
            AND download_token_hash IS NULL
            AND package_expires_at IS NULL
            AND download_count = 0
            AND completed_at IS NOT NULL
        )
    ),
    INDEX idx_data_export_job__user_status_created (user_id, status, created_at),
    INDEX idx_data_export_job__subject_created (subject_hash, created_at),
    INDEX idx_data_export_job__status_package_expiry (status, package_expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE account_deletion_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    privacy_request_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    cancellable_until DATETIME(3) NOT NULL,
    cancelled_at DATETIME(3) NULL,
    purge_scheduled_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    safe_failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    previous_user_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_deletion_slot TINYINT UNSIGNED
        GENERATED ALWAYS AS (
            IF(status IN ('PENDING', 'SCHEDULED', 'PURGING', 'FAILED'), 1, NULL)
        ) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_account_deletion_request PRIMARY KEY (id),
    CONSTRAINT uq_account_deletion_request__external_id UNIQUE (external_id),
    CONSTRAINT uq_account_deletion_request__privacy_request UNIQUE (privacy_request_id),
    CONSTRAINT uq_account_deletion_request__user_active UNIQUE (user_id, active_deletion_slot),
    CONSTRAINT fk_account_deletion_request__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_account_deletion_request__request_id__privacy_request
        FOREIGN KEY (privacy_request_id) REFERENCES privacy_request (id) ON DELETE SET NULL,
    CONSTRAINT ck_account_deletion_request__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_account_deletion_request__status CHECK (
        status IN (
            'PENDING', 'CANCELLED', 'SCHEDULED', 'PURGING',
            'COMPLETED', 'COMPLETED_WITH_RETENTION', 'FAILED'
        )
    ),
    CONSTRAINT ck_account_deletion_request__previous_status CHECK (
        previous_user_status IN ('ACTIVE', 'LIMITED')
    ),
    CONSTRAINT ck_account_deletion_request__request_times CHECK (
        requested_at >= created_at AND cancellable_until > requested_at
    ),
    CONSTRAINT ck_account_deletion_request__lifecycle CHECK (
        (
            status = 'PENDING'
            AND cancelled_at IS NULL
            AND purge_scheduled_at IS NULL
            AND completed_at IS NULL
            AND safe_failure_code IS NULL
        )
        OR (
            status = 'CANCELLED'
            AND cancelled_at BETWEEN requested_at AND cancellable_until
            AND purge_scheduled_at IS NULL
            AND completed_at IS NULL
            AND safe_failure_code IS NULL
        )
        OR (
            status = 'SCHEDULED'
            AND cancelled_at IS NULL
            AND purge_scheduled_at >= cancellable_until
            AND completed_at IS NULL
            AND safe_failure_code IS NULL
        )
        OR (
            status = 'PURGING'
            AND cancelled_at IS NULL
            AND purge_scheduled_at IS NOT NULL
            AND completed_at IS NULL
            AND safe_failure_code IS NULL
        )
        OR (
            status IN ('COMPLETED', 'COMPLETED_WITH_RETENTION')
            AND cancelled_at IS NULL
            AND purge_scheduled_at IS NOT NULL
            AND completed_at >= purge_scheduled_at
            AND safe_failure_code IS NULL
        )
        OR (
            status = 'FAILED'
            AND cancelled_at IS NULL
            AND purge_scheduled_at IS NOT NULL
            AND completed_at IS NULL
            AND safe_failure_code IS NOT NULL
        )
    ),
    INDEX idx_account_deletion_request__user_status (user_id, status),
    INDEX idx_account_deletion_request__subject_created (subject_hash, created_at),
    INDEX idx_account_deletion_request__status_schedule (status, purge_scheduled_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE retention_policy (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    retention_days INT UNSIGNED NULL,
    trash_days INT UNSIGNED NULL,
    legal_basis VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    retired_at DATETIME(3) NULL,
    active_policy_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (IF(status = 'ACTIVE', policy_key, NULL)) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_retention_policy PRIMARY KEY (id),
    CONSTRAINT uq_retention_policy__external_id UNIQUE (external_id),
    CONSTRAINT uq_retention_policy__policy_version UNIQUE (policy_key, version_no),
    CONSTRAINT uq_retention_policy__active_key UNIQUE (active_policy_key),
    CONSTRAINT ck_retention_policy__policy_key CHECK (
        CHAR_LENGTH(TRIM(policy_key)) > 0
    ),
    CONSTRAINT ck_retention_policy__version_no CHECK (
        version_no > 0
    ),
    CONSTRAINT ck_retention_policy__object_type CHECK (
        CHAR_LENGTH(TRIM(object_type)) > 0
    ),
    CONSTRAINT ck_retention_policy__durations CHECK (
        (retention_days IS NULL OR retention_days > 0)
        AND (trash_days IS NULL OR trash_days > 0)
    ),
    CONSTRAINT ck_retention_policy__legal_basis CHECK (
        legal_basis IS NULL OR CHAR_LENGTH(TRIM(legal_basis)) > 0
    ),
    CONSTRAINT ck_retention_policy__status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_retention_policy__lifecycle CHECK (
        (status IN ('DRAFT', 'ACTIVE') AND retired_at IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= effective_at)
    ),
    INDEX idx_retention_policy__status_effective (status, effective_at),
    INDEX idx_retention_policy__object_type (object_type)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE retention_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_id BIGINT UNSIGNED NOT NULL,
    async_job_id BIGINT UNSIGNED NOT NULL,
    cutoff_at DATETIME(3) NOT NULL,
    examined_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    purged_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    skipped_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    completed_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_retention_run PRIMARY KEY (id),
    CONSTRAINT uq_retention_run__external_id UNIQUE (external_id),
    CONSTRAINT uq_retention_run__async_job_id UNIQUE (async_job_id),
    CONSTRAINT fk_retention_run__policy_id__retention_policy
        FOREIGN KEY (policy_id) REFERENCES retention_policy (id),
    CONSTRAINT fk_retention_run__async_job_id__async_job
        FOREIGN KEY (async_job_id) REFERENCES async_job (id),
    CONSTRAINT ck_retention_run__status CHECK (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_retention_run__counts CHECK (
        purged_count + skipped_count <= examined_count
    ),
    CONSTRAINT ck_retention_run__lifecycle CHECK (
        (status IN ('QUEUED', 'RUNNING') AND completed_at IS NULL)
        OR (
            status IN ('COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED')
            AND completed_at IS NOT NULL
            AND completed_at >= created_at
        )
    ),
    INDEX idx_retention_run__policy_created (policy_id, created_at),
    INDEX idx_retention_run__status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE legal_hold (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    object_ref_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_by_admin_id BIGINT UNSIGNED NOT NULL,
    authorized_by_admin_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    starts_at DATETIME(3) NOT NULL,
    ends_at DATETIME(3) NULL,
    review_due_at DATETIME(3) NOT NULL,
    released_at DATETIME(3) NULL,
    active_scope_ref_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            IF(status = 'ACTIVE', COALESCE(object_ref_hash, subject_hash), NULL)
        ) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_legal_hold PRIMARY KEY (id),
    CONSTRAINT uq_legal_hold__external_id UNIQUE (external_id),
    CONSTRAINT uq_legal_hold__active_scope UNIQUE (scope_type, active_scope_ref_hash),
    CONSTRAINT fk_legal_hold__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_legal_hold__requested_admin_id__admin_user
        FOREIGN KEY (requested_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT fk_legal_hold__authorized_admin_id__admin_user
        FOREIGN KEY (authorized_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT ck_legal_hold__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_legal_hold__scope_type CHECK (
        scope_type IN ('USER', 'OBJECT')
    ),
    CONSTRAINT ck_legal_hold__scope_shape CHECK (
        (scope_type = 'USER' AND object_type IS NULL AND object_ref_hash IS NULL)
        OR (
            scope_type = 'OBJECT'
            AND object_type IS NOT NULL
            AND CHAR_LENGTH(TRIM(object_type)) > 0
            AND object_ref_hash REGEXP '^[0-9a-f]{64}$'
        )
    ),
    CONSTRAINT ck_legal_hold__reason_code CHECK (
        CHAR_LENGTH(TRIM(reason_code)) > 0
    ),
    CONSTRAINT ck_legal_hold__separate_approval CHECK (
        requested_by_admin_id <> authorized_by_admin_id
    ),
    CONSTRAINT ck_legal_hold__status CHECK (
        status IN ('ACTIVE', 'RELEASED', 'EXPIRED')
    ),
    CONSTRAINT ck_legal_hold__time_window CHECK (
        starts_at >= created_at
        AND (ends_at IS NULL OR ends_at > starts_at)
        AND review_due_at > starts_at
        AND review_due_at <= DATE_ADD(starts_at, INTERVAL 90 DAY)
    ),
    CONSTRAINT ck_legal_hold__lifecycle CHECK (
        (status = 'ACTIVE' AND released_at IS NULL)
        OR (
            status = 'RELEASED'
            AND released_at IS NOT NULL
            AND released_at >= starts_at
        )
        OR (
            status = 'EXPIRED'
            AND ends_at IS NOT NULL
            AND released_at IS NULL
        )
    ),
    INDEX idx_legal_hold__user_status (user_id, status),
    INDEX idx_legal_hold__subject_status (subject_hash, status),
    INDEX idx_legal_hold__status_review (status, review_due_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE deletion_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_deletion_request_id BIGINT UNSIGNED NULL,
    privacy_request_id BIGINT UNSIGNED NULL,
    retention_run_id BIGINT UNSIGNED NULL,
    async_job_id BIGINT UNSIGNED NOT NULL,
    trigger_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    safe_failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_deletion_job PRIMARY KEY (id),
    CONSTRAINT uq_deletion_job__external_id UNIQUE (external_id),
    CONSTRAINT uq_deletion_job__account_request UNIQUE (account_deletion_request_id),
    CONSTRAINT uq_deletion_job__privacy_request UNIQUE (privacy_request_id),
    CONSTRAINT uq_deletion_job__retention_run UNIQUE (retention_run_id),
    CONSTRAINT uq_deletion_job__async_job_id UNIQUE (async_job_id),
    CONSTRAINT fk_deletion_job__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_deletion_job__account_request_id__account_deletion
        FOREIGN KEY (account_deletion_request_id) REFERENCES account_deletion_request (id) ON DELETE SET NULL,
    CONSTRAINT fk_deletion_job__privacy_request_id__privacy_request
        FOREIGN KEY (privacy_request_id) REFERENCES privacy_request (id) ON DELETE SET NULL,
    CONSTRAINT fk_deletion_job__retention_run_id__retention_run
        FOREIGN KEY (retention_run_id) REFERENCES retention_run (id) ON DELETE SET NULL,
    CONSTRAINT fk_deletion_job__async_job_id__async_job
        FOREIGN KEY (async_job_id) REFERENCES async_job (id),
    CONSTRAINT ck_deletion_job__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_deletion_job__trigger_type CHECK (
        trigger_type IN (
            'ACCOUNT_DELETION', 'PRIVACY_REQUEST', 'USER_OBJECT_DELETE',
            'RETENTION', 'SYSTEM_CLEANUP'
        )
    ),
    CONSTRAINT ck_deletion_job__scope_type CHECK (
        scope_type IN ('ACCOUNT', 'USER_DATA', 'ASSET', 'LEARNING_PROJECT', 'EXPORT_PACKAGE', 'RETENTION_BATCH')
    ),
    CONSTRAINT ck_deletion_job__status CHECK (
        status IN (
            'QUEUED', 'RUNNING', 'RETRY_WAIT', 'COMPLETED',
            'COMPLETED_WITH_RETENTION', 'FAILED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_deletion_job__lifecycle CHECK (
        (
            status = 'QUEUED'
            AND started_at IS NULL
            AND completed_at IS NULL
            AND safe_failure_code IS NULL
        )
        OR (
            status = 'RUNNING'
            AND started_at IS NOT NULL
            AND started_at >= created_at
            AND completed_at IS NULL
            AND safe_failure_code IS NULL
        )
        OR (
            status IN ('RETRY_WAIT', 'FAILED')
            AND started_at IS NOT NULL
            AND completed_at IS NULL
            AND safe_failure_code IS NOT NULL
        )
        OR (
            status IN ('COMPLETED', 'COMPLETED_WITH_RETENTION', 'CANCELLED')
            AND started_at IS NOT NULL
            AND completed_at >= started_at
            AND safe_failure_code IS NULL
        )
    ),
    INDEX idx_deletion_job__user_status (user_id, status),
    INDEX idx_deletion_job__subject_created (subject_hash, created_at),
    INDEX idx_deletion_job__status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE deletion_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    deletion_job_id BIGINT UNSIGNED NOT NULL,
    store_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    retention_policy_id BIGINT UNSIGNED NULL,
    legal_hold_id BIGINT UNSIGNED NULL,
    completed_at DATETIME(3) NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_deletion_item PRIMARY KEY (id),
    CONSTRAINT uq_deletion_item__external_id UNIQUE (external_id),
    CONSTRAINT uq_deletion_item__job_store_object UNIQUE (
        deletion_job_id, store_type, object_type, object_ref_hash
    ),
    CONSTRAINT fk_deletion_item__job_id__deletion_job
        FOREIGN KEY (deletion_job_id) REFERENCES deletion_job (id) ON DELETE CASCADE,
    CONSTRAINT fk_deletion_item__policy_id__retention_policy
        FOREIGN KEY (retention_policy_id) REFERENCES retention_policy (id),
    CONSTRAINT fk_deletion_item__legal_hold_id__legal_hold
        FOREIGN KEY (legal_hold_id) REFERENCES legal_hold (id),
    CONSTRAINT ck_deletion_item__store_type CHECK (
        store_type IN ('MYSQL', 'OBJECT_STORAGE', 'SEARCH_INDEX', 'CACHE')
    ),
    CONSTRAINT ck_deletion_item__object_type CHECK (
        CHAR_LENGTH(TRIM(object_type)) > 0
    ),
    CONSTRAINT ck_deletion_item__object_ref_hash CHECK (
        object_ref_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_deletion_item__status CHECK (
        status IN ('PENDING', 'DELETING', 'DELETED', 'ABSENT', 'RETAINED', 'BLOCKED', 'FAILED')
    ),
    CONSTRAINT ck_deletion_item__attempt_count CHECK (
        attempt_count <= 6
    ),
    CONSTRAINT ck_deletion_item__retention_basis CHECK (
        (
            status = 'RETAINED'
            AND (
                (retention_policy_id IS NOT NULL AND legal_hold_id IS NULL)
                OR (retention_policy_id IS NULL AND legal_hold_id IS NOT NULL)
            )
        )
        OR (
            status <> 'RETAINED'
            AND retention_policy_id IS NULL
            AND legal_hold_id IS NULL
        )
    ),
    CONSTRAINT ck_deletion_item__lifecycle CHECK (
        (
            status = 'PENDING'
            AND attempt_count = 0
            AND completed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status = 'DELETING'
            AND attempt_count > 0
            AND completed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status IN ('DELETED', 'ABSENT', 'RETAINED')
            AND completed_at IS NOT NULL
            AND completed_at >= created_at
            AND failure_code IS NULL
        )
        OR (
            status IN ('BLOCKED', 'FAILED')
            AND attempt_count > 0
            AND completed_at IS NULL
            AND failure_code IS NOT NULL
        )
    ),
    INDEX idx_deletion_item__status_updated (status, updated_at),
    INDEX idx_deletion_item__policy_id (retention_policy_id),
    INDEX idx_deletion_item__legal_hold_id (legal_hold_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE data_tombstone (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purged_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NULL,
    reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_data_tombstone PRIMARY KEY (id),
    CONSTRAINT uq_data_tombstone__external_id UNIQUE (external_id),
    CONSTRAINT uq_data_tombstone__subject_object UNIQUE (subject_hash, object_type, object_ref_hash),
    CONSTRAINT ck_data_tombstone__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_data_tombstone__object_type CHECK (
        CHAR_LENGTH(TRIM(object_type)) > 0
    ),
    CONSTRAINT ck_data_tombstone__object_ref_hash CHECK (
        object_ref_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_data_tombstone__reason_code CHECK (
        CHAR_LENGTH(TRIM(reason_code)) > 0
    ),
    CONSTRAINT ck_data_tombstone__times CHECK (
        purged_at >= created_at AND (expires_at IS NULL OR expires_at > purged_at)
    ),
    INDEX idx_data_tombstone__expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE admin_access_case (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_by_admin_id BIGINT UNSIGNED NOT NULL,
    approved_by_admin_id BIGINT UNSIGNED NULL,
    user_id BIGINT UNSIGNED NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    decision_at DATETIME(3) NULL,
    approved_at DATETIME(3) NULL,
    expires_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    reason VARCHAR(1000) NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_admin_access_case PRIMARY KEY (id),
    CONSTRAINT uq_admin_access_case__external_id UNIQUE (external_id),
    CONSTRAINT fk_admin_access_case__requested_admin_id__admin_user
        FOREIGN KEY (requested_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT fk_admin_access_case__approved_admin_id__admin_user
        FOREIGN KEY (approved_by_admin_id) REFERENCES admin_user (id),
    CONSTRAINT fk_admin_access_case__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_admin_access_case__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_admin_access_case__purpose_key CHECK (
        CHAR_LENGTH(TRIM(purpose_key)) > 0
    ),
    CONSTRAINT ck_admin_access_case__status CHECK (
        status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'REVOKED', 'EXPIRED', 'CLOSED')
    ),
    CONSTRAINT ck_admin_access_case__reason CHECK (
        CHAR_LENGTH(TRIM(reason)) > 0
    ),
    CONSTRAINT ck_admin_access_case__separate_approval CHECK (
        approved_by_admin_id IS NULL OR requested_by_admin_id <> approved_by_admin_id
    ),
    CONSTRAINT ck_admin_access_case__requested_time CHECK (
        requested_at >= created_at
    ),
    CONSTRAINT ck_admin_access_case__lifecycle CHECK (
        (
            status = 'PENDING_APPROVAL'
            AND approved_by_admin_id IS NULL
            AND decision_at IS NULL
            AND approved_at IS NULL
            AND expires_at IS NULL
            AND closed_at IS NULL
        )
        OR (
            status = 'ACTIVE'
            AND approved_by_admin_id IS NOT NULL
            AND decision_at IS NOT NULL
            AND approved_at = decision_at
            AND approved_at >= requested_at
            AND expires_at > approved_at
            AND expires_at <= DATE_ADD(approved_at, INTERVAL 1 HOUR)
            AND closed_at IS NULL
        )
        OR (
            status = 'REJECTED'
            AND approved_by_admin_id IS NOT NULL
            AND decision_at >= requested_at
            AND approved_at IS NULL
            AND expires_at IS NULL
            AND closed_at = decision_at
        )
        OR (
            status IN ('REVOKED', 'EXPIRED', 'CLOSED')
            AND approved_by_admin_id IS NOT NULL
            AND decision_at IS NOT NULL
            AND approved_at IS NOT NULL
            AND expires_at > approved_at
            AND closed_at IS NOT NULL
            AND closed_at >= approved_at
        )
    ),
    INDEX idx_admin_access_case__user_status_expiry (user_id, status, expires_at),
    INDEX idx_admin_access_case__subject_status_expiry (subject_hash, status, expires_at),
    INDEX idx_admin_access_case__requester_created (requested_by_admin_id, created_at),
    INDEX idx_admin_access_case__approver_created (approved_by_admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE admin_access_grant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_id BIGINT UNSIGNED NOT NULL,
    object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    permission VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    starts_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    target_ref_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (COALESCE(object_ref_hash, REPEAT('0', 64))) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_admin_access_grant PRIMARY KEY (id),
    CONSTRAINT uq_admin_access_grant__external_id UNIQUE (external_id),
    CONSTRAINT uq_admin_access_grant__case_target_permission UNIQUE (
        case_id, object_type, target_ref_key, permission
    ),
    CONSTRAINT fk_admin_access_grant__case_id__admin_access_case
        FOREIGN KEY (case_id) REFERENCES admin_access_case (id) ON DELETE CASCADE,
    CONSTRAINT ck_admin_access_grant__object_type CHECK (
        object_type IN ('USER_ACCOUNT', 'ASSET', 'CONVERSATION', 'LEARNING_PROJECT', 'ASSESSMENT_ATTEMPT')
    ),
    CONSTRAINT ck_admin_access_grant__object_ref_hash CHECK (
        object_ref_hash IS NULL OR object_ref_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_admin_access_grant__permission CHECK (
        permission IN ('READ_METADATA', 'READ_CONTENT')
    ),
    CONSTRAINT ck_admin_access_grant__content_target CHECK (
        permission <> 'READ_CONTENT' OR object_ref_hash IS NOT NULL
    ),
    CONSTRAINT ck_admin_access_grant__time_window CHECK (
        starts_at >= created_at
        AND expires_at > starts_at
        AND expires_at <= DATE_ADD(starts_at, INTERVAL 1 HOUR)
    ),
    INDEX idx_admin_access_grant__case_expiry (case_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE admin_access_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_id BIGINT UNSIGNED NULL,
    admin_user_id BIGINT UNSIGNED NOT NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    result VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_admin_access_audit PRIMARY KEY (id),
    CONSTRAINT uq_admin_access_audit__external_id UNIQUE (external_id),
    CONSTRAINT fk_admin_access_audit__case_id__admin_access_case
        FOREIGN KEY (case_id) REFERENCES admin_access_case (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_access_audit__admin_user_id__admin_user
        FOREIGN KEY (admin_user_id) REFERENCES admin_user (id),
    CONSTRAINT ck_admin_access_audit__subject_hash CHECK (
        subject_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_admin_access_audit__action_type CHECK (
        CHAR_LENGTH(TRIM(action_type)) > 0
    ),
    CONSTRAINT ck_admin_access_audit__object_type CHECK (
        CHAR_LENGTH(TRIM(object_type)) > 0
    ),
    CONSTRAINT ck_admin_access_audit__object_ref_hash CHECK (
        object_ref_hash IS NULL OR object_ref_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_admin_access_audit__occurred_time CHECK (
        occurred_at >= created_at
    ),
    CONSTRAINT ck_admin_access_audit__result CHECK (
        result IN ('ALLOWED', 'DENIED', 'ERROR')
    ),
    CONSTRAINT ck_admin_access_audit__result_code CHECK (
        (result = 'ALLOWED' AND result_code IS NULL)
        OR (result IN ('DENIED', 'ERROR') AND result_code IS NOT NULL)
    ),
    INDEX idx_admin_access_audit__case_time (case_id, occurred_at),
    INDEX idx_admin_access_audit__admin_time (admin_user_id, occurred_at),
    INDEX idx_admin_access_audit__subject_time (subject_hash, occurred_at),
    INDEX idx_admin_access_audit__request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
