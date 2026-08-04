-- ExamInsight V2 model registry, routing policy and prompt catalog.
-- This migration creates configuration structure only. It never inserts provider
-- credentials, model registrations, prompt bodies or test data.

CREATE TABLE model_provider (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) NULL,
    credential_secret_ref VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    region VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    timeout_ms INT UNSIGNED NOT NULL,
    metadata_json JSON NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_model_provider PRIMARY KEY (id),
    CONSTRAINT uq_model_provider__external_id UNIQUE (external_id),
    CONSTRAINT uq_model_provider__provider_key UNIQUE (provider_key),
    CONSTRAINT ck_model_provider__status CHECK (
        status IN ('ACTIVE', 'DISABLED')
    ),
    CONSTRAINT ck_model_provider__timeout CHECK (
        timeout_ms BETWEEN 1 AND 600000
    ),
    CONSTRAINT ck_model_provider__credential_ref CHECK (
        CHAR_LENGTH(TRIM(credential_secret_ref)) > 0
    ),
    CONSTRAINT ck_model_provider__metadata_object CHECK (
        metadata_json IS NULL OR JSON_TYPE(metadata_json) = 'OBJECT'
    ),
    INDEX idx_model_provider__status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE model_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_id BIGINT UNSIGNED NOT NULL,
    model_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_model_name VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    context_token_limit BIGINT UNSIGNED NULL,
    max_output_tokens INT UNSIGNED NULL,
    supports_tools BOOLEAN NOT NULL DEFAULT FALSE,
    supports_json_schema BOOLEAN NOT NULL DEFAULT FALSE,
    supports_streaming BOOLEAN NOT NULL DEFAULT FALSE,
    capability_json JSON NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_model_definition PRIMARY KEY (id),
    CONSTRAINT uq_model_definition__external_id UNIQUE (external_id),
    CONSTRAINT uq_model_definition__provider_name UNIQUE (provider_id, provider_model_name),
    CONSTRAINT uq_model_definition__model_key UNIQUE (model_key),
    CONSTRAINT fk_model_definition__provider_id__model_provider
        FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    CONSTRAINT ck_model_definition__role CHECK (
        role IN ('FAST', 'REASONING', 'VALIDATOR', 'EMBEDDING', 'OCR', 'IMAGE')
    ),
    CONSTRAINT ck_model_definition__status CHECK (
        status IN ('ACTIVE', 'DISABLED', 'DEPRECATED')
    ),
    CONSTRAINT ck_model_definition__context_limit CHECK (
        context_token_limit IS NULL OR context_token_limit > 0
    ),
    CONSTRAINT ck_model_definition__output_limit CHECK (
        max_output_tokens IS NULL OR max_output_tokens > 0
    ),
    CONSTRAINT ck_model_definition__capability_object CHECK (
        capability_json IS NULL OR JSON_TYPE(capability_json) = 'OBJECT'
    ),
    INDEX idx_model_definition__role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE model_policy_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description VARCHAR(1000) NULL,
    effective_at DATETIME(3) NOT NULL,
    retired_at DATETIME(3) NULL,
    fallback_mode VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_model_policy_version PRIMARY KEY (id),
    CONSTRAINT uq_model_policy_version__external_id UNIQUE (external_id),
    CONSTRAINT uq_model_policy_version__policy_version UNIQUE (policy_key, version_no),
    CONSTRAINT ck_model_policy_version__version_no CHECK (version_no > 0),
    CONSTRAINT ck_model_policy_version__status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_model_policy_version__fallback_mode CHECK (
        fallback_mode IN ('FAIL_CLOSED', 'SAME_ROLE', 'ALLOW_DEGRADED')
    ),
    CONSTRAINT ck_model_policy_version__retired_time CHECK (
        (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= effective_at)
        OR (status <> 'RETIRED' AND retired_at IS NULL)
    ),
    INDEX idx_model_policy_version__status_effective (status, effective_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE model_policy_route (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    priority INT UNSIGNED NOT NULL,
    model_definition_id BIGINT UNSIGNED NOT NULL,
    condition_json JSON NULL,
    timeout_ms INT UNSIGNED NOT NULL,
    max_retries INT UNSIGNED NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_model_policy_route PRIMARY KEY (id),
    CONSTRAINT uq_model_policy_route__external_id UNIQUE (external_id),
    CONSTRAINT uq_model_policy_route__policy_role_priority UNIQUE (policy_version_id, role, priority),
    CONSTRAINT fk_model_policy_route__policy_id__policy_version
        FOREIGN KEY (policy_version_id) REFERENCES model_policy_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_model_policy_route__model_id__model_definition
        FOREIGN KEY (model_definition_id) REFERENCES model_definition (id),
    CONSTRAINT ck_model_policy_route__role CHECK (
        role IN ('FAST', 'REASONING', 'VALIDATOR', 'EMBEDDING', 'OCR', 'IMAGE')
    ),
    CONSTRAINT ck_model_policy_route__priority CHECK (priority > 0),
    CONSTRAINT ck_model_policy_route__timeout CHECK (
        timeout_ms BETWEEN 1 AND 600000
    ),
    CONSTRAINT ck_model_policy_route__max_retries CHECK (max_retries <= 10),
    CONSTRAINT ck_model_policy_route__condition_object CHECK (
        condition_json IS NULL OR JSON_TYPE(condition_json) = 'OBJECT'
    ),
    INDEX idx_model_policy_route__model_id (model_definition_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE prompt_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prompt_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    purpose VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    current_version_id BIGINT UNSIGNED NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_prompt_template PRIMARY KEY (id),
    CONSTRAINT uq_prompt_template__external_id UNIQUE (external_id),
    CONSTRAINT uq_prompt_template__prompt_key UNIQUE (prompt_key),
    CONSTRAINT ck_prompt_template__status CHECK (
        status IN ('ACTIVE', 'DISABLED')
    ),
    INDEX idx_prompt_template__status (status),
    INDEX idx_prompt_template__current_version (current_version_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE prompt_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prompt_template_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    system_template LONGTEXT NOT NULL,
    developer_template LONGTEXT NULL,
    input_schema_json JSON NOT NULL,
    output_schema_json JSON NOT NULL,
    template_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_at DATETIME(3) NULL,
    retired_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_prompt_version PRIMARY KEY (id),
    CONSTRAINT uq_prompt_version__external_id UNIQUE (external_id),
    CONSTRAINT uq_prompt_version__template_version UNIQUE (prompt_template_id, version_no),
    CONSTRAINT uq_prompt_version__template_hash UNIQUE (prompt_template_id, template_hash),
    CONSTRAINT fk_prompt_version__template_id__prompt_template
        FOREIGN KEY (prompt_template_id) REFERENCES prompt_template (id) ON DELETE CASCADE,
    CONSTRAINT ck_prompt_version__version_no CHECK (version_no > 0),
    CONSTRAINT ck_prompt_version__system_template CHECK (
        CHAR_LENGTH(TRIM(system_template)) > 0
    ),
    CONSTRAINT ck_prompt_version__status CHECK (
        status IN ('DRAFT', 'PUBLISHED', 'RETIRED')
    ),
    CONSTRAINT ck_prompt_version__lifecycle_time CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND retired_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL AND retired_at >= published_at)
    ),
    CONSTRAINT ck_prompt_version__input_schema_object CHECK (
        JSON_TYPE(input_schema_json) = 'OBJECT'
    ),
    CONSTRAINT ck_prompt_version__output_schema_object CHECK (
        JSON_TYPE(output_schema_json) = 'OBJECT'
    ),
    INDEX idx_prompt_version__status_published (status, published_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
