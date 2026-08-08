ALTER TABLE app_user
    DROP COLUMN age_gate_acknowledged_at;

CREATE TABLE terms_document_version (
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
    CONSTRAINT pk_terms_document_version PRIMARY KEY (id),
    CONSTRAINT uq_terms_document_version__external_id UNIQUE (external_id),
    CONSTRAINT uq_terms_document_version__key_locale UNIQUE (version_key, locale),
    CONSTRAINT uq_terms_document_version__active_locale UNIQUE (active_locale_key),
    CONSTRAINT ck_terms_document_version__version_key CHECK (
        CHAR_LENGTH(TRIM(version_key)) > 0
    ),
    CONSTRAINT ck_terms_document_version__locale CHECK (
        CHAR_LENGTH(TRIM(locale)) > 0
    ),
    CONSTRAINT ck_terms_document_version__content_hash CHECK (
        content_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_terms_document_version__content_url CHECK (
        CHAR_LENGTH(TRIM(content_url)) > 0
        AND (content_url LIKE 'https://%' OR content_url LIKE '/%')
    ),
    CONSTRAINT ck_terms_document_version__status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_terms_document_version__lifecycle CHECK (
        (status IN ('DRAFT', 'ACTIVE') AND retired_at IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= effective_at)
    ),
    INDEX idx_terms_document_version__status_effective (status, effective_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE terms_acceptance (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    terms_version_id BIGINT UNSIGNED NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    ip_prefix_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_terms_acceptance PRIMARY KEY (id),
    CONSTRAINT uq_terms_acceptance__external_id UNIQUE (external_id),
    CONSTRAINT uq_terms_acceptance__user_version UNIQUE (user_id, terms_version_id),
    CONSTRAINT fk_terms_acceptance__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_terms_acceptance__version_id__terms_document_version
        FOREIGN KEY (terms_version_id) REFERENCES terms_document_version (id),
    CONSTRAINT ck_terms_acceptance__ip_hash CHECK (
        ip_prefix_hash IS NULL OR ip_prefix_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_terms_acceptance__user_agent_hash CHECK (
        user_agent_hash IS NULL OR user_agent_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_terms_acceptance__accepted_time CHECK (
        accepted_at >= created_at
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

UPDATE privacy_notice_version
   SET status = 'RETIRED',
       retired_at = GREATEST(effective_at, TIMESTAMP('2026-08-08 00:00:00')),
       row_version = row_version + 1
 WHERE locale = 'zh-CN' AND status = 'ACTIVE';

INSERT INTO terms_document_version (
    external_id, version_key, locale, content_hash, content_url,
    status, effective_at, retired_at
) VALUES (
    '01K25TERMS0000000000000000',
    '2026-08-08-beta.1',
    'zh-CN',
    'f7d85d3ea58f6994f271c67d5a324ead8d1fb170c5649bd540d86cc92c2ba706',
    '/terms',
    'ACTIVE',
    '2026-08-08 00:00:00.000',
    NULL
);

INSERT INTO privacy_notice_version (
    external_id, version_key, locale, content_hash, content_url,
    status, effective_at, retired_at
) VALUES (
    '01K25PRIVACY00000000000000',
    '2026-08-08-beta.1',
    'zh-CN',
    '0f2614eb8738be59ab23c7a2ada1046fbe6c03d61a8b06021fd7af94e466bde6',
    '/privacy',
    'ACTIVE',
    '2026-08-08 00:00:00.000',
    NULL
);
