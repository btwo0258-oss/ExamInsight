-- ExamInsight V2 upload, storage and user-owned asset records.
-- This migration creates structure only. Object payloads stay in object storage,
-- and vector values stay in the rebuildable search index.

CREATE TABLE upload_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    upload_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    declared_mime VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expected_size BIGINT UNSIGNED NOT NULL,
    expected_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    multipart_upload_ref_ciphertext VARBINARY(1024) NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    part_size INT UNSIGNED NOT NULL DEFAULT 8388608,
    uploaded_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_upload_session PRIMARY KEY (id),
    CONSTRAINT uq_upload_session__external_id UNIQUE (external_id),
    CONSTRAINT uq_upload_session__user_upload_key UNIQUE (user_id, upload_key),
    CONSTRAINT fk_upload_session__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_upload_session__status CHECK (
        status IN ('INITIATED', 'UPLOADING', 'COMPLETING', 'COMPLETED', 'ABORTED', 'EXPIRED', 'FAILED')
    ),
    CONSTRAINT ck_upload_session__upload_key CHECK (
        CHAR_LENGTH(TRIM(upload_key)) > 0
    ),
    CONSTRAINT ck_upload_session__filename CHECK (
        CHAR_LENGTH(TRIM(original_filename)) > 0
    ),
    CONSTRAINT ck_upload_session__declared_mime CHECK (
        declared_mime IS NULL OR CHAR_LENGTH(TRIM(declared_mime)) > 0
    ),
    CONSTRAINT ck_upload_session__size CHECK (
        expected_size > 0 AND expected_size <= 104857600
    ),
    CONSTRAINT ck_upload_session__expected_sha256 CHECK (
        expected_sha256 IS NULL OR expected_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_upload_session__multipart_ref CHECK (
        multipart_upload_ref_ciphertext IS NULL OR OCTET_LENGTH(multipart_upload_ref_ciphertext) > 0
    ),
    CONSTRAINT ck_upload_session__part_size CHECK (
        part_size = 8388608
    ),
    CONSTRAINT ck_upload_session__uploaded_bytes CHECK (
        uploaded_bytes <= expected_size
    ),
    CONSTRAINT ck_upload_session__initiated_bytes CHECK (
        status <> 'INITIATED' OR uploaded_bytes = 0
    ),
    CONSTRAINT ck_upload_session__expiry CHECK (
        expires_at > created_at
    ),
    CONSTRAINT ck_upload_session__completion CHECK (
        (
            status = 'COMPLETED'
            AND completed_at IS NOT NULL
            AND completed_at BETWEEN created_at AND expires_at
            AND uploaded_bytes = expected_size
        )
        OR (
            status <> 'COMPLETED'
            AND completed_at IS NULL
        )
    ),
    INDEX idx_upload_session__user_status_updated (user_id, status, updated_at),
    INDEX idx_upload_session__status_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE storage_object (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_user_id BIGINT UNSIGNED NULL,
    bucket_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_key_ciphertext VARBINARY(1024) NOT NULL,
    object_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    mime_type VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    encryption_key_ref VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    verified_at DATETIME(3) NULL,
    scanner_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    scanner_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    scan_completed_at DATETIME(3) NULL,
    safe_rejection_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    purged_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_storage_object PRIMARY KEY (id),
    CONSTRAINT uq_storage_object__external_id UNIQUE (external_id),
    CONSTRAINT uq_storage_object__bucket_object_hash UNIQUE (bucket_key, object_key_hash),
    CONSTRAINT fk_storage_object__owner_user_id__app_user
        FOREIGN KEY (owner_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_storage_object__status CHECK (
        status IN ('QUARANTINED', 'SCANNING', 'AVAILABLE', 'REJECTED', 'PURGING', 'PURGED')
    ),
    CONSTRAINT ck_storage_object__bucket_key CHECK (
        CHAR_LENGTH(TRIM(bucket_key)) > 0
    ),
    CONSTRAINT ck_storage_object__object_key_ciphertext CHECK (
        OCTET_LENGTH(object_key_ciphertext) > 0
    ),
    CONSTRAINT ck_storage_object__object_key_hash CHECK (
        object_key_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_storage_object__sha256 CHECK (
        sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_storage_object__size CHECK (
        size_bytes > 0
    ),
    CONSTRAINT ck_storage_object__mime CHECK (
        CHAR_LENGTH(TRIM(mime_type)) > 0
    ),
    CONSTRAINT ck_storage_object__encryption_ref CHECK (
        encryption_key_ref IS NULL OR CHAR_LENGTH(TRIM(encryption_key_ref)) > 0
    ),
    CONSTRAINT ck_storage_object__scanner_pair CHECK (
        (scanner_key IS NULL AND scanner_version IS NULL)
        OR (scanner_key IS NOT NULL AND scanner_version IS NOT NULL)
    ),
    CONSTRAINT ck_storage_object__time_order CHECK (
        (verified_at IS NULL OR verified_at >= created_at)
        AND (scan_completed_at IS NULL OR scan_completed_at >= COALESCE(verified_at, created_at))
        AND (purged_at IS NULL OR purged_at >= created_at)
    ),
    CONSTRAINT ck_storage_object__lifecycle CHECK (
        (
            status = 'QUARANTINED'
            AND scan_completed_at IS NULL
            AND safe_rejection_code IS NULL
            AND purged_at IS NULL
        )
        OR (
            status = 'SCANNING'
            AND verified_at IS NOT NULL
            AND scanner_key IS NOT NULL
            AND scan_completed_at IS NULL
            AND safe_rejection_code IS NULL
            AND purged_at IS NULL
        )
        OR (
            status = 'AVAILABLE'
            AND verified_at IS NOT NULL
            AND scanner_key IS NOT NULL
            AND scan_completed_at IS NOT NULL
            AND safe_rejection_code IS NULL
            AND purged_at IS NULL
        )
        OR (
            status = 'REJECTED'
            AND verified_at IS NOT NULL
            AND scanner_key IS NOT NULL
            AND scan_completed_at IS NOT NULL
            AND safe_rejection_code IS NOT NULL
            AND purged_at IS NULL
        )
        OR (
            status = 'PURGING'
            AND purged_at IS NULL
        )
        OR (
            status = 'PURGED'
            AND purged_at IS NOT NULL
        )
    ),
    INDEX idx_storage_object__owner_status (owner_user_id, status),
    INDEX idx_storage_object__sha256_size (sha256, size_bytes),
    INDEX idx_storage_object__status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    asset_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    current_version_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trash_started_at DATETIME(3) NULL,
    previous_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    deleted_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_asset PRIMARY KEY (id),
    CONSTRAINT uq_asset__external_id UNIQUE (external_id),
    CONSTRAINT fk_asset__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_asset__name CHECK (
        CHAR_LENGTH(TRIM(name)) > 0
    ),
    CONSTRAINT ck_asset__type CHECK (
        asset_type IN ('FILE', 'TEXT')
    ),
    CONSTRAINT ck_asset__source CHECK (
        source_type IN ('UPLOAD', 'USER_TEXT', 'AI_GENERATED', 'LEGACY_IMPORT')
    ),
    CONSTRAINT ck_asset__source_shape CHECK (
        (source_type = 'UPLOAD' AND asset_type = 'FILE')
        OR (source_type = 'USER_TEXT' AND asset_type = 'TEXT')
        OR source_type IN ('AI_GENERATED', 'LEGACY_IMPORT')
    ),
    CONSTRAINT ck_asset__status CHECK (
        status IN ('ACTIVE', 'ARCHIVED', 'TRASHED', 'PURGED')
    ),
    CONSTRAINT ck_asset__previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('ACTIVE', 'ARCHIVED')
    ),
    CONSTRAINT ck_asset__lifecycle CHECK (
        (
            status IN ('ACTIVE', 'ARCHIVED')
            AND trash_started_at IS NULL
            AND previous_status IS NULL
            AND deleted_at IS NULL
        )
        OR (
            status IN ('TRASHED', 'PURGED')
            AND trash_started_at IS NOT NULL
            AND previous_status IN ('ACTIVE', 'ARCHIVED')
            AND deleted_at IS NOT NULL
            AND deleted_at >= trash_started_at
        )
    ),
    INDEX idx_asset__user_status_updated (user_id, status, updated_at),
    INDEX idx_asset__user_name (user_id, name),
    INDEX idx_asset__current_version_id (current_version_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE asset_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    asset_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    upload_session_id BIGINT UNSIGNED NULL,
    storage_object_id BIGINT UNSIGNED NULL,
    text_content LONGTEXT NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mime_type VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_parse_result_id BIGINT UNSIGNED NULL,
    generated_by_ai BOOLEAN NOT NULL DEFAULT FALSE,
    ai_run_id BIGINT UNSIGNED NULL,
    generation_label VARCHAR(160) NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_asset_version PRIMARY KEY (id),
    CONSTRAINT uq_asset_version__external_id UNIQUE (external_id),
    CONSTRAINT uq_asset_version__asset_version_no UNIQUE (asset_id, version_no),
    CONSTRAINT uq_asset_version__asset_content_hash UNIQUE (asset_id, content_sha256),
    CONSTRAINT uq_asset_version__upload_session_id UNIQUE (upload_session_id),
    CONSTRAINT fk_asset_version__asset_id__asset
        FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_version__upload_session_id__upload_session
        FOREIGN KEY (upload_session_id) REFERENCES upload_session (id) ON DELETE SET NULL,
    CONSTRAINT fk_asset_version__storage_object_id__storage_object
        FOREIGN KEY (storage_object_id) REFERENCES storage_object (id),
    CONSTRAINT fk_asset_version__created_by_user_id__app_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_asset_version__version_no CHECK (
        version_no > 0
    ),
    CONSTRAINT ck_asset_version__content_source CHECK (
        (storage_object_id IS NOT NULL AND text_content IS NULL)
        OR (storage_object_id IS NULL AND text_content IS NOT NULL)
    ),
    CONSTRAINT ck_asset_version__text_size CHECK (
        text_content IS NULL OR OCTET_LENGTH(text_content) = size_bytes
    ),
    CONSTRAINT ck_asset_version__content_sha256 CHECK (
        content_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_asset_version__mime CHECK (
        CHAR_LENGTH(TRIM(mime_type)) > 0
    ),
    CONSTRAINT ck_asset_version__size CHECK (
        size_bytes > 0
    ),
    CONSTRAINT ck_asset_version__source CHECK (
        source_type IN ('UPLOAD', 'USER_TEXT', 'AI_GENERATED', 'LEGACY_IMPORT')
    ),
    CONSTRAINT ck_asset_version__source_shape CHECK (
        (source_type = 'UPLOAD' AND storage_object_id IS NOT NULL)
        OR (source_type = 'USER_TEXT' AND text_content IS NOT NULL)
        OR source_type IN ('AI_GENERATED', 'LEGACY_IMPORT')
    ),
    CONSTRAINT ck_asset_version__status CHECK (
        status IN ('QUARANTINED', 'PROCESSING', 'READY', 'FAILED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_asset_version__scan_status CHECK (
        status NOT IN ('QUARANTINED', 'REJECTED') OR storage_object_id IS NOT NULL
    ),
    CONSTRAINT ck_asset_version__generated_flag CHECK (
        generated_by_ai IN (FALSE, TRUE)
    ),
    CONSTRAINT ck_asset_version__generation_metadata CHECK (
        (
            source_type = 'AI_GENERATED'
            AND generated_by_ai = TRUE
            AND generation_label IS NOT NULL
            AND CHAR_LENGTH(TRIM(generation_label)) > 0
        )
        OR (
            source_type <> 'AI_GENERATED'
            AND generated_by_ai = FALSE
            AND ai_run_id IS NULL
            AND generation_label IS NULL
        )
    ),
    INDEX idx_asset_version__storage_object_id (storage_object_id),
    INDEX idx_asset_version__active_parse_result_id (active_parse_result_id),
    INDEX idx_asset_version__ai_run_id (ai_run_id),
    INDEX idx_asset_version__creator_created (created_by_user_id, created_at),
    INDEX idx_asset_version__status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE asset_parse_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    asset_version_id BIGINT UNSIGNED NOT NULL,
    parser_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parser_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    async_job_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    language VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    page_count INT UNSIGNED NULL,
    chunk_count INT UNSIGNED NOT NULL DEFAULT 0,
    plain_text_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    safe_error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    completed_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_asset_parse_result PRIMARY KEY (id),
    CONSTRAINT uq_asset_parse_result__external_id UNIQUE (external_id),
    CONSTRAINT uq_asset_parse_result__version_parser UNIQUE (asset_version_id, parser_key, parser_version),
    CONSTRAINT uq_asset_parse_result__async_job_id UNIQUE (async_job_id),
    CONSTRAINT fk_asset_parse_result__version_id__asset_version
        FOREIGN KEY (asset_version_id) REFERENCES asset_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_parse_result__async_job_id__async_job
        FOREIGN KEY (async_job_id) REFERENCES async_job (id),
    CONSTRAINT ck_asset_parse_result__parser_key CHECK (
        CHAR_LENGTH(TRIM(parser_key)) > 0
    ),
    CONSTRAINT ck_asset_parse_result__parser_version CHECK (
        CHAR_LENGTH(TRIM(parser_version)) > 0
    ),
    CONSTRAINT ck_asset_parse_result__status CHECK (
        status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_asset_parse_result__language CHECK (
        language IS NULL OR CHAR_LENGTH(TRIM(language)) > 0
    ),
    CONSTRAINT ck_asset_parse_result__page_count CHECK (
        page_count IS NULL OR page_count BETWEEN 1 AND 1000
    ),
    CONSTRAINT ck_asset_parse_result__plain_text_hash CHECK (
        plain_text_sha256 IS NULL OR plain_text_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_asset_parse_result__completion_time CHECK (
        completed_at IS NULL OR completed_at >= created_at
    ),
    CONSTRAINT ck_asset_parse_result__lifecycle CHECK (
        (
            status IN ('QUEUED', 'PROCESSING')
            AND chunk_count = 0
            AND plain_text_sha256 IS NULL
            AND safe_error_code IS NULL
            AND completed_at IS NULL
        )
        OR (
            status = 'READY'
            AND chunk_count > 0
            AND plain_text_sha256 IS NOT NULL
            AND safe_error_code IS NULL
            AND completed_at IS NOT NULL
        )
        OR (
            status = 'FAILED'
            AND chunk_count = 0
            AND plain_text_sha256 IS NULL
            AND safe_error_code IS NOT NULL
            AND completed_at IS NOT NULL
        )
        OR (
            status = 'CANCELLED'
            AND chunk_count = 0
            AND plain_text_sha256 IS NULL
            AND safe_error_code IS NULL
            AND completed_at IS NOT NULL
        )
    ),
    INDEX idx_asset_parse_result__status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE document_chunk (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parse_result_id BIGINT UNSIGNED NOT NULL,
    sequence_no INT UNSIGNED NOT NULL,
    content LONGTEXT NOT NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_count INT UNSIGNED NOT NULL,
    page_from INT UNSIGNED NULL,
    page_to INT UNSIGNED NULL,
    locator_json JSON NULL,
    heading_path VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_document_chunk PRIMARY KEY (id),
    CONSTRAINT uq_document_chunk__external_id UNIQUE (external_id),
    CONSTRAINT uq_document_chunk__result_sequence UNIQUE (parse_result_id, sequence_no),
    CONSTRAINT fk_document_chunk__parse_result_id__asset_parse_result
        FOREIGN KEY (parse_result_id) REFERENCES asset_parse_result (id) ON DELETE CASCADE,
    CONSTRAINT ck_document_chunk__sequence_no CHECK (
        sequence_no > 0
    ),
    CONSTRAINT ck_document_chunk__content CHECK (
        CHAR_LENGTH(TRIM(content)) > 0 AND OCTET_LENGTH(content) <= 65536
    ),
    CONSTRAINT ck_document_chunk__content_sha256 CHECK (
        content_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_document_chunk__token_count CHECK (
        token_count BETWEEN 1 AND 2000
    ),
    CONSTRAINT ck_document_chunk__page_range CHECK (
        (page_from IS NULL AND page_to IS NULL)
        OR (page_from IS NOT NULL AND page_to IS NOT NULL AND page_from > 0 AND page_from <= page_to)
    ),
    CONSTRAINT ck_document_chunk__heading_path CHECK (
        heading_path IS NULL OR CHAR_LENGTH(TRIM(heading_path)) > 0
    ),
    INDEX idx_document_chunk__result_page (parse_result_id, page_from)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE embedding_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    chunk_id BIGINT UNSIGNED NOT NULL,
    model_definition_id BIGINT UNSIGNED NOT NULL,
    embedding_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    index_name VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    index_document_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    indexed_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_embedding_record PRIMARY KEY (id),
    CONSTRAINT uq_embedding_record__external_id UNIQUE (external_id),
    CONSTRAINT uq_embedding_record__chunk_model_version UNIQUE (chunk_id, model_definition_id, embedding_version),
    CONSTRAINT fk_embedding_record__chunk_id__document_chunk
        FOREIGN KEY (chunk_id) REFERENCES document_chunk (id) ON DELETE CASCADE,
    CONSTRAINT fk_embedding_record__model_definition_id__model_definition
        FOREIGN KEY (model_definition_id) REFERENCES model_definition (id),
    CONSTRAINT ck_embedding_record__embedding_version CHECK (
        CHAR_LENGTH(TRIM(embedding_version)) > 0
    ),
    CONSTRAINT ck_embedding_record__index_name CHECK (
        CHAR_LENGTH(TRIM(index_name)) > 0
    ),
    CONSTRAINT ck_embedding_record__index_document_id CHECK (
        CHAR_LENGTH(TRIM(index_document_id)) > 0
    ),
    CONSTRAINT ck_embedding_record__content_sha256 CHECK (
        content_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_embedding_record__status CHECK (
        status IN ('PENDING', 'INDEXING', 'INDEXED', 'FAILED', 'DELETING', 'DELETED')
    ),
    CONSTRAINT ck_embedding_record__time_order CHECK (
        (indexed_at IS NULL OR indexed_at >= created_at)
        AND (deleted_at IS NULL OR (indexed_at IS NOT NULL AND deleted_at >= indexed_at))
    ),
    CONSTRAINT ck_embedding_record__lifecycle CHECK (
        (
            status IN ('PENDING', 'INDEXING', 'FAILED')
            AND indexed_at IS NULL
            AND deleted_at IS NULL
        )
        OR (
            status IN ('INDEXED', 'DELETING')
            AND indexed_at IS NOT NULL
            AND deleted_at IS NULL
        )
        OR (
            status = 'DELETED'
            AND indexed_at IS NOT NULL
            AND deleted_at IS NOT NULL
        )
    ),
    INDEX idx_embedding_record__model_status (model_definition_id, status),
    INDEX idx_embedding_record__status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
