-- ExamInsight V2 platform foundation.
-- Target schema: examinsight_v2 (MySQL 8+, InnoDB, utf8mb4).
-- app_user is introduced by V003. The nullable user_id columns are indexed here;
-- their foreign keys are added only after app_user exists.

CREATE TABLE async_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    job_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    aggregate_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stage_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    progress_current BIGINT UNSIGNED NOT NULL DEFAULT 0,
    progress_total BIGINT UNSIGNED NULL,
    priority SMALLINT UNSIGNED NOT NULL DEFAULT 100,
    idempotency_scope VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    cancellable BOOLEAN NOT NULL DEFAULT FALSE,
    payload_json JSON NULL,
    result_json JSON NULL,
    scheduled_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at DATETIME(3) NULL,
    heartbeat_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    safe_error_message VARCHAR(500) NULL,
    lease_owner VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expires_at DATETIME(3) NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 3,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_async_job PRIMARY KEY (id),
    CONSTRAINT uq_async_job__external_id UNIQUE (external_id),
    CONSTRAINT uq_async_job__idempotency UNIQUE (idempotency_scope, job_type, idempotency_key),
    CONSTRAINT ck_async_job__status CHECK (
        status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLING', 'CANCELLED')
    ),
    CONSTRAINT ck_async_job__idempotency_pair CHECK (
        (idempotency_scope IS NULL AND idempotency_key IS NULL)
        OR (idempotency_scope IS NOT NULL AND idempotency_key IS NOT NULL)
    ),
    CONSTRAINT ck_async_job__lease_pair CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_async_job__progress CHECK (
        progress_total IS NULL OR progress_current <= progress_total
    ),
    CONSTRAINT ck_async_job__attempts CHECK (
        max_attempts > 0 AND attempt_count <= max_attempts
    ),
    INDEX idx_async_job__user_id (user_id),
    INDEX idx_async_job__status_schedule_priority (status, scheduled_at, priority),
    INDEX idx_async_job__aggregate_ref (aggregate_type, aggregate_external_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE async_job_attempt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    async_job_id BIGINT UNSIGNED NOT NULL,
    attempt_no INT UNSIGNED NOT NULL,
    worker_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    heartbeat_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    diagnostic_json JSON NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_async_job_attempt PRIMARY KEY (id),
    CONSTRAINT uq_async_job_attempt__external_id UNIQUE (external_id),
    CONSTRAINT uq_async_job_attempt__job_attempt UNIQUE (async_job_id, attempt_no),
    CONSTRAINT fk_async_job_attempt__job_id__async_job
        FOREIGN KEY (async_job_id) REFERENCES async_job (id) ON DELETE CASCADE,
    CONSTRAINT ck_async_job_attempt__attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_async_job_attempt__status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'LEASE_EXPIRED')
    ),
    INDEX idx_async_job_attempt__worker_status (worker_id, status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_version INT UNSIGNED NOT NULL DEFAULT 1,
    payload_json JSON NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_owner VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expires_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event__external_id UNIQUE (external_id),
    CONSTRAINT ck_outbox_event__event_version CHECK (event_version > 0),
    CONSTRAINT ck_outbox_event__status CHECK (
        status IN ('PENDING', 'PUBLISHING', 'RETRY_WAIT', 'PUBLISHED', 'DEAD_LETTER')
    ),
    CONSTRAINT ck_outbox_event__status_lease CHECK (
        (status = 'PUBLISHING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'PUBLISHING' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_outbox_event__published_at CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    INDEX idx_outbox_event__status_available (status, available_at),
    INDEX idx_outbox_event__aggregate_ref (aggregate_type, aggregate_external_id, id),
    INDEX idx_outbox_event__lease_expiry (status, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    actor_scope VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PROCESSING',
    response_status SMALLINT UNSIGNED NULL,
    response_ref_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    response_ref_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expires_at DATETIME(3) NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_idempotency_record PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_record__external_id UNIQUE (external_id),
    CONSTRAINT uq_idempotency_record__actor_scope_key UNIQUE (actor_scope, scope_key, idempotency_key),
    CONSTRAINT ck_idempotency_record__status CHECK (
        status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_idempotency_record__response_status CHECK (
        response_status IS NULL OR response_status BETWEEN 100 AND 599
    ),
    CONSTRAINT ck_idempotency_record__processing_result CHECK (
        status <> 'PROCESSING'
        OR (response_status IS NULL AND response_ref_type IS NULL AND response_ref_external_id IS NULL)
    ),
    INDEX idx_idempotency_record__user_expiry (user_id, expires_at),
    INDEX idx_idempotency_record__expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE domain_audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    actor_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    aggregate_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_version BIGINT UNSIGNED NULL,
    new_version BIGINT UNSIGNED NULL,
    reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    change_summary_json JSON NULL,
    request_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_domain_audit_event PRIMARY KEY (id),
    CONSTRAINT uq_domain_audit_event__external_id UNIQUE (external_id),
    INDEX idx_domain_audit_event__aggregate_time (aggregate_type, aggregate_external_id, occurred_at),
    INDEX idx_domain_audit_event__user_time (user_id, occurred_at),
    INDEX idx_domain_audit_event__request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE legacy_import_map (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    async_job_id BIGINT UNSIGNED NOT NULL,
    source_schema VARCHAR(64) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    source_primary_key VARCHAR(128) NOT NULL,
    source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    target_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    target_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_legacy_import_map PRIMARY KEY (id),
    CONSTRAINT uq_legacy_import_map__external_id UNIQUE (external_id),
    CONSTRAINT uq_legacy_import_map__source_record UNIQUE (source_schema, source_table, source_primary_key),
    CONSTRAINT fk_legacy_import_map__job_id__async_job
        FOREIGN KEY (async_job_id) REFERENCES async_job (id),
    CONSTRAINT ck_legacy_import_map__status CHECK (
        status IN ('PENDING', 'IMPORTED', 'SKIPPED', 'FAILED')
    ),
    CONSTRAINT ck_legacy_import_map__target_pair CHECK (
        (target_type IS NULL AND target_external_id IS NULL)
        OR (target_type IS NOT NULL AND target_external_id IS NOT NULL)
    ),
    CONSTRAINT ck_legacy_import_map__imported_target CHECK (
        status <> 'IMPORTED' OR target_external_id IS NOT NULL
    ),
    INDEX idx_legacy_import_map__job_status (async_job_id, status),
    INDEX idx_legacy_import_map__target_ref (target_type, target_external_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
