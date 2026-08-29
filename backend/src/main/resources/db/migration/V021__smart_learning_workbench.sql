-- Phase 2: executable learning workbench state.  These tables are deliberately
-- separate from the preparation JSON so execution can be resumed without
-- rewriting a confirmed plan.

ALTER TABLE smart_learning_job DROP CHECK ck_smart_learning_job__kind;
ALTER TABLE smart_learning_job ADD CONSTRAINT ck_smart_learning_job__kind CHECK (
    kind IN ('SCOPE_ANALYSIS', 'DIAGNOSIS_GENERATION', 'PLAN_GENERATION', 'RESOURCE_PREPARATION')
);

CREATE TABLE smart_learning_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    project_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    plan_version INT UNSIGNED NOT NULL,
    source_task_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(240) NOT NULL,
    task_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'READING',
    description VARCHAR(1000) NULL,
    completion_criteria VARCHAR(1000) NULL,
    scheduled_date DATE NULL,
    duration_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    status VARCHAR(28) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PLANNED',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    payload_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_smart_learning_task PRIMARY KEY (id),
    CONSTRAINT uq_smart_learning_task__external_id UNIQUE (external_id),
    CONSTRAINT uq_smart_learning_task__project_source UNIQUE (project_external_id, plan_version, source_task_id),
    CONSTRAINT fk_smart_learning_task__project FOREIGN KEY (project_external_id) REFERENCES smart_learning_project (external_id) ON DELETE CASCADE,
    CONSTRAINT fk_smart_learning_task__user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_smart_learning_task__type CHECK (task_type IN ('READING', 'EXERCISE', 'REVIEW', 'EXPLANATION')),
    CONSTRAINT ck_smart_learning_task__status CHECK (status IN ('PLANNED', 'AVAILABLE', 'IN_PROGRESS', 'PAUSED', 'COMPLETION_PENDING', 'COMPLETED', 'SKIPPED', 'CANCELLED')),
    INDEX idx_smart_learning_task__project_date (project_external_id, scheduled_date, sort_order),
    INDEX idx_smart_learning_task__user_status (user_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE smart_learning_resource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    project_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(240) NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'QUEUED',
    content_json JSON NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_smart_learning_resource PRIMARY KEY (id),
    CONSTRAINT uq_smart_learning_resource__external_id UNIQUE (external_id),
    CONSTRAINT uq_smart_learning_resource__task_kind UNIQUE (task_external_id, kind),
    CONSTRAINT fk_smart_learning_resource__project FOREIGN KEY (project_external_id) REFERENCES smart_learning_project (external_id) ON DELETE CASCADE,
    CONSTRAINT fk_smart_learning_resource__task FOREIGN KEY (task_external_id) REFERENCES smart_learning_task (external_id) ON DELETE CASCADE,
    CONSTRAINT fk_smart_learning_resource__user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_smart_learning_resource__kind CHECK (kind IN ('READING', 'EXERCISE_SET')),
    CONSTRAINT ck_smart_learning_resource__status CHECK (status IN ('QUEUED', 'GENERATING', 'READY', 'FAILED')),
    INDEX idx_smart_learning_resource__project_status (project_external_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE smart_learning_execution (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    project_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(28) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'IN_PROGRESS',
    progress DECIMAL(5,2) NOT NULL DEFAULT 0,
    accumulated_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    position_json JSON NULL,
    answers_json JSON NULL,
    score DECIMAL(6,2) NULL,
    last_heartbeat_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    paused_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_smart_learning_execution PRIMARY KEY (id),
    CONSTRAINT uq_smart_learning_execution__external_id UNIQUE (external_id),
    CONSTRAINT fk_smart_learning_execution__project FOREIGN KEY (project_external_id) REFERENCES smart_learning_project (external_id) ON DELETE CASCADE,
    CONSTRAINT fk_smart_learning_execution__task FOREIGN KEY (task_external_id) REFERENCES smart_learning_task (external_id) ON DELETE CASCADE,
    CONSTRAINT fk_smart_learning_execution__user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_smart_learning_execution__status CHECK (status IN ('IN_PROGRESS', 'PAUSED', 'COMPLETION_PENDING', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT ck_smart_learning_execution__progress CHECK (progress >= 0 AND progress <= 100),
    INDEX idx_smart_learning_execution__task_user (task_external_id, user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
