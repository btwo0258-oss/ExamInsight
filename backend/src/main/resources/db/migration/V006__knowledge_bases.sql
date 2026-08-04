-- ExamInsight V2 personal knowledge-base containers and asset membership.
-- A knowledge base never copies an asset, asset version, parse result or vector.

-- Required by the ownership-preserving composite foreign key below. The primary
-- key still identifies the asset; this unique key proves which user owns it.
ALTER TABLE asset
    ADD CONSTRAINT uq_asset__id_user_id UNIQUE (id, user_id);

CREATE TABLE knowledge_base (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trash_started_at DATETIME(3) NULL,
    previous_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    deleted_at DATETIME(3) NULL,
    active_name_key VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        GENERATED ALWAYS AS (
            IF(status IN ('ACTIVE', 'ARCHIVED'), normalized_name, NULL)
        ) STORED,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_knowledge_base PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_base__external_id UNIQUE (external_id),
    CONSTRAINT uq_knowledge_base__id_user_id UNIQUE (id, user_id),
    CONSTRAINT uq_knowledge_base__user_active_name UNIQUE (user_id, active_name_key),
    CONSTRAINT fk_knowledge_base__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_knowledge_base__name CHECK (
        CHAR_LENGTH(name) > 0
        AND name = TRIM(name)
        AND name NOT REGEXP '[[:cntrl:]]'
    ),
    CONSTRAINT ck_knowledge_base__normalized_name CHECK (
        CHAR_LENGTH(normalized_name) > 0
        AND normalized_name = TRIM(normalized_name)
        AND normalized_name NOT REGEXP '[[:cntrl:]]'
    ),
    CONSTRAINT ck_knowledge_base__description CHECK (
        description IS NULL
        OR (
            CHAR_LENGTH(description) > 0
            AND description = TRIM(description)
            AND description NOT REGEXP '[[:cntrl:]]'
        )
    ),
    CONSTRAINT ck_knowledge_base__status CHECK (
        status IN ('ACTIVE', 'ARCHIVED', 'TRASHED', 'PURGED')
    ),
    CONSTRAINT ck_knowledge_base__previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('ACTIVE', 'ARCHIVED')
    ),
    CONSTRAINT ck_knowledge_base__lifecycle CHECK (
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
    INDEX idx_knowledge_base__user_status_updated (user_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_base_asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT UNSIGNED NOT NULL,
    asset_id BIGINT UNSIGNED NOT NULL,
    added_by_user_id BIGINT UNSIGNED NOT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_knowledge_base_asset PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_base_asset__base_asset UNIQUE (knowledge_base_id, asset_id),
    CONSTRAINT fk_kb_asset__base_user__knowledge_base
        FOREIGN KEY (knowledge_base_id, added_by_user_id)
        REFERENCES knowledge_base (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_asset__asset_user__asset
        FOREIGN KEY (asset_id, added_by_user_id)
        REFERENCES asset (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_base_asset__sort_order CHECK (
        sort_order <= 1000000
    ),
    INDEX idx_kb_asset__base_user (knowledge_base_id, added_by_user_id),
    INDEX idx_kb_asset__asset_user (asset_id, added_by_user_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
