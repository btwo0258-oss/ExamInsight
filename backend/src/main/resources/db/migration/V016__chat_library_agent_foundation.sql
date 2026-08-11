-- V2 chat/library/agent foundation.
-- Generated assets are not indexed automatically because they are not trusted source material.

ALTER TABLE asset_version
    ADD COLUMN rag_policy VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'AUTO' AFTER generation_label,
    ADD COLUMN rag_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_INDEXED' AFTER rag_policy,
    ADD COLUMN rag_error_code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER rag_status,
    ADD COLUMN indexed_at DATETIME(3) NULL AFTER rag_error_code,
    ADD CONSTRAINT ck_asset_version__rag_policy CHECK (
        rag_policy IN ('AUTO', 'MANUAL', 'DISABLED')
    ),
    ADD CONSTRAINT ck_asset_version__rag_status CHECK (
        rag_status IN ('NOT_INDEXED', 'PENDING', 'INDEXING', 'INDEXED', 'FAILED', 'DISABLED')
    ),
    ADD CONSTRAINT ck_asset_version__rag_disabled CHECK (
        (rag_policy = 'DISABLED' AND rag_status = 'DISABLED')
        OR rag_policy <> 'DISABLED'
    ),
    ADD INDEX idx_asset_version__rag_status (rag_policy, rag_status, updated_at);

UPDATE asset_version
SET rag_policy = 'MANUAL',
    rag_status = 'NOT_INDEXED',
    rag_error_code = NULL,
    indexed_at = NULL
WHERE generated_by_ai = TRUE OR source_type = 'AI_GENERATED';

UPDATE asset_version av
SET av.rag_status = 'INDEXED',
    av.indexed_at = COALESCE(
        (SELECT MAX(er.created_at)
         FROM embedding_record er
         INNER JOIN document_chunk dc ON dc.id = er.chunk_id
         INNER JOIN asset_parse_result pr ON pr.id = dc.parse_result_id
         WHERE pr.asset_version_id = av.id
           AND er.status = 'INDEXED'),
        av.indexed_at
    )
WHERE av.rag_policy = 'AUTO'
  AND EXISTS (
      SELECT 1
      FROM embedding_record er
      INNER JOIN document_chunk dc ON dc.id = er.chunk_id
      INNER JOIN asset_parse_result pr ON pr.id = dc.parse_result_id
      WHERE pr.asset_version_id = av.id
        AND er.status = 'INDEXED'
  );

CREATE TABLE asset_preview_derivative (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    asset_version_id BIGINT UNSIGNED NOT NULL,
    derivative_kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    storage_object_id BIGINT UNSIGNED NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mime_type VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
    size_bytes BIGINT UNSIGNED NULL,
    page_count INT UNSIGNED NULL,
    error_code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_asset_preview_derivative PRIMARY KEY (id),
    CONSTRAINT uq_asset_preview_derivative__external_id UNIQUE (external_id),
    CONSTRAINT uq_asset_preview_derivative__version_kind UNIQUE (asset_version_id, derivative_kind),
    CONSTRAINT fk_asset_preview_derivative__version_id__asset_version
        FOREIGN KEY (asset_version_id) REFERENCES asset_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_preview_derivative__storage_id__storage_object
        FOREIGN KEY (storage_object_id) REFERENCES storage_object (id) ON DELETE SET NULL,
    CONSTRAINT ck_asset_preview_derivative__kind CHECK (
        derivative_kind IN ('PDF', 'PAGE_IMAGE', 'THUMBNAIL', 'TEXT_RENDER')
    ),
    CONSTRAINT ck_asset_preview_derivative__status CHECK (
        status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED', 'NOT_SUPPORTED')
    ),
    CONSTRAINT ck_asset_preview_derivative__ready_shape CHECK (
        status <> 'READY' OR storage_object_id IS NOT NULL
    ),
    INDEX idx_asset_preview_derivative__status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Freeze every source version used by an AI run. Knowledge-base membership may change later,
-- but old answers and citations must remain reproducible.
CREATE TABLE ai_context_source (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    context_snapshot_id BIGINT UNSIGNED NOT NULL,
    source_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    knowledge_base_id BIGINT UNSIGNED NULL,
    asset_id BIGINT UNSIGNED NOT NULL,
    asset_version_id BIGINT UNSIGNED NOT NULL,
    source_order INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ai_context_source PRIMARY KEY (id),
    CONSTRAINT uq_ai_context_source__snapshot_version UNIQUE (context_snapshot_id, asset_version_id),
    CONSTRAINT uq_ai_context_source__snapshot_order UNIQUE (context_snapshot_id, source_order),
    CONSTRAINT fk_ai_context_source__snapshot_id__ai_context_snapshot
        FOREIGN KEY (context_snapshot_id) REFERENCES ai_context_snapshot (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_context_source__knowledge_base_id__knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_context_source__asset_id__asset
        FOREIGN KEY (asset_id) REFERENCES asset (id),
    CONSTRAINT fk_ai_context_source__asset_version_id__asset_version
        FOREIGN KEY (asset_version_id) REFERENCES asset_version (id),
    CONSTRAINT ck_ai_context_source__kind CHECK (
        source_kind IN ('KNOWLEDGE_BASE', 'DIRECT_ASSET')
    ),
    CONSTRAINT ck_ai_context_source__base_shape CHECK (
        (source_kind = 'KNOWLEDGE_BASE' AND knowledge_base_id IS NOT NULL)
        OR (source_kind = 'DIRECT_ASSET' AND knowledge_base_id IS NULL)
    ),
    INDEX idx_ai_context_source__version_id (asset_version_id),
    INDEX idx_ai_context_source__knowledge_base_id (knowledge_base_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE artifact_draft (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    ai_run_id BIGINT UNSIGNED NULL,
    request_message_id BIGINT UNSIGNED NULL,
    artifact_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(255) NOT NULL,
    schema_version INT UNSIGNED NOT NULL DEFAULT 1,
    content_json JSON NOT NULL,
    current_revision_no INT UNSIGNED NOT NULL DEFAULT 1,
    confirmed_asset_version_id BIGINT UNSIGNED NULL,
    error_code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    confirmed_at DATETIME(3) NULL,
    CONSTRAINT pk_artifact_draft PRIMARY KEY (id),
    CONSTRAINT uq_artifact_draft__external_id UNIQUE (external_id),
    CONSTRAINT uq_artifact_draft__confirmed_version UNIQUE (confirmed_asset_version_id),
    CONSTRAINT fk_artifact_draft__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_artifact_draft__conversation_id__conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_artifact_draft__ai_run_id__ai_run
        FOREIGN KEY (ai_run_id) REFERENCES ai_run (id) ON DELETE SET NULL,
    CONSTRAINT fk_artifact_draft__request_message_id__message
        FOREIGN KEY (request_message_id) REFERENCES message (id) ON DELETE SET NULL,
    CONSTRAINT fk_artifact_draft__confirmed_version_id__asset_version
        FOREIGN KEY (confirmed_asset_version_id) REFERENCES asset_version (id) ON DELETE SET NULL,
    CONSTRAINT ck_artifact_draft__type CHECK (
        artifact_type IN ('DOCUMENT', 'MINDMAP', 'PRESENTATION', 'IMAGE')
    ),
    CONSTRAINT ck_artifact_draft__status CHECK (
        status IN ('GENERATING', 'DRAFT', 'READY', 'CONFIRMED', 'CANCELLED', 'FAILED')
    ),
    CONSTRAINT ck_artifact_draft__title CHECK (
        CHAR_LENGTH(TRIM(title)) > 0
    ),
    CONSTRAINT ck_artifact_draft__content CHECK (
        JSON_TYPE(content_json) = 'OBJECT'
    ),
    CONSTRAINT ck_artifact_draft__confirmation CHECK (
        (status = 'CONFIRMED' AND confirmed_asset_version_id IS NOT NULL AND confirmed_at IS NOT NULL)
        OR (status <> 'CONFIRMED' AND confirmed_at IS NULL)
    ),
    INDEX idx_artifact_draft__conversation_status (conversation_id, status, updated_at),
    INDEX idx_artifact_draft__user_type (user_id, artifact_type, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE artifact_revision (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    artifact_draft_id BIGINT UNSIGNED NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    origin VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_json JSON NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by_user_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_artifact_revision PRIMARY KEY (id),
    CONSTRAINT uq_artifact_revision__draft_revision UNIQUE (artifact_draft_id, revision_no),
    CONSTRAINT fk_artifact_revision__draft_id__artifact_draft
        FOREIGN KEY (artifact_draft_id) REFERENCES artifact_draft (id) ON DELETE CASCADE,
    CONSTRAINT fk_artifact_revision__created_by_user_id__app_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_artifact_revision__origin CHECK (
        origin IN ('AI', 'USER')
    ),
    CONSTRAINT ck_artifact_revision__content CHECK (
        JSON_TYPE(content_json) = 'OBJECT'
    ),
    CONSTRAINT ck_artifact_revision__content_hash CHECK (
        content_hash REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE model_invocation
    ADD COLUMN input_cost_micros BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER cached_input_tokens,
    ADD COLUMN output_cost_micros BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER input_cost_micros,
    ADD COLUMN total_cost_micros BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER output_cost_micros,
    ADD COLUMN cost_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' AFTER total_cost_micros,
    ADD CONSTRAINT ck_model_invocation__cost_total CHECK (
        total_cost_micros = input_cost_micros + output_cost_micros
    ),
    ADD CONSTRAINT ck_model_invocation__currency CHECK (
        cost_currency REGEXP '^[A-Z]{3}$'
    );
