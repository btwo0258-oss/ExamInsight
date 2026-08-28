-- Smart learning preparation workflow for the V2 application.
-- The legacy learning tables remain untouched.  Drafts and confirmed versions
-- are intentionally stored separately so an edit cannot silently change the
-- inputs of a running or already confirmed AI job.

CREATE TABLE smart_learning_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(160) NOT NULL,
    icon VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'book',
    icon_color VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '#2f6fed',
    knowledge_base_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    stage VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TARGET_REQUIRED',
    archived_previous_stage VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    target_version INT UNSIGNED NOT NULL DEFAULT 0,
    target_json JSON NULL,
    target_draft_json JSON NULL,
    source_version INT UNSIGNED NOT NULL DEFAULT 0,
    sources_json JSON NULL,
    sources_draft_json JSON NULL,
    scope_version INT UNSIGNED NOT NULL DEFAULT 0,
    scope_json JSON NULL,
    scope_candidate_json JSON NULL,
    diagnosis_version INT UNSIGNED NOT NULL DEFAULT 0,
    diagnosis_json JSON NULL,
    diagnosis_candidate_json JSON NULL,
    diagnosis_answers_draft_json JSON NULL,
    plan_version INT UNSIGNED NOT NULL DEFAULT 0,
    plan_json JSON NULL,
    plan_candidate_json JSON NULL,
    resource_config_version INT UNSIGNED NOT NULL DEFAULT 0,
    resource_config_json JSON NULL,
    resource_config_draft_json JSON NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_smart_learning_project PRIMARY KEY (id),
    CONSTRAINT uq_smart_learning_project__external_id UNIQUE (external_id),
    CONSTRAINT fk_smart_learning_project__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_smart_learning_project__name CHECK (
        CHAR_LENGTH(TRIM(name)) > 0 AND CHAR_LENGTH(name) <= 160
    ),
    CONSTRAINT ck_smart_learning_project__stage CHECK (
        stage IN (
            'TARGET_REQUIRED', 'SOURCES_REQUIRED', 'SCOPE_REQUIRED',
            'DIAGNOSTIC_REQUIRED', 'PLAN_REQUIRED',
            'RESOURCE_CONFIG_REQUIRED', 'READY', 'ARCHIVED'
        )
    ),
    INDEX idx_smart_learning_project__user_updated (user_id, updated_at, id),
    INDEX idx_smart_learning_project__user_stage (user_id, stage)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE smart_learning_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    project_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'QUEUED',
    progress_current INT UNSIGNED NOT NULL DEFAULT 0,
    progress_total INT UNSIGNED NOT NULL DEFAULT 1,
    result_json JSON NULL,
    safe_error_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_smart_learning_job PRIMARY KEY (id),
    CONSTRAINT uq_smart_learning_job__external_id UNIQUE (external_id),
    CONSTRAINT fk_smart_learning_job__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_smart_learning_job__project_external_id__project
        FOREIGN KEY (project_external_id) REFERENCES smart_learning_project (external_id) ON DELETE CASCADE,
    CONSTRAINT ck_smart_learning_job__kind CHECK (
        kind IN ('SCOPE_ANALYSIS', 'DIAGNOSIS_GENERATION', 'PLAN_GENERATION')
    ),
    CONSTRAINT ck_smart_learning_job__status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'UNKNOWN')
    ),
    CONSTRAINT ck_smart_learning_job__progress CHECK (progress_current <= progress_total),
    INDEX idx_smart_learning_job__project_kind (project_external_id, kind, created_at),
    INDEX idx_smart_learning_job__user_status (user_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
