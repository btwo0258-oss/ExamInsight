-- Branch-scoped, auditable conversation summaries.  Raw messages remain the
-- source of truth; this table only stores a bounded lossy checkpoint.
CREATE TABLE conversation_memory_checkpoint (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    branch_id BIGINT UNSIGNED NOT NULL,
    covered_through_message_id BIGINT UNSIGNED NOT NULL,
    covered_through_sequence BIGINT UNSIGNED NOT NULL,
    source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    summary_json JSON NOT NULL,
    summary_tokens INT UNSIGNED NOT NULL DEFAULT 0,
    prompt_version_id BIGINT UNSIGNED NULL,
    model_name VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_conversation_memory_checkpoint PRIMARY KEY (id),
    CONSTRAINT uq_conversation_memory_checkpoint__external_id UNIQUE (external_id),
    CONSTRAINT uq_conversation_memory_checkpoint__branch_message UNIQUE (branch_id, covered_through_message_id),
    CONSTRAINT fk_conversation_memory_checkpoint__user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_memory_checkpoint__conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_memory_checkpoint__branch FOREIGN KEY (branch_id) REFERENCES conversation_branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_memory_checkpoint__message FOREIGN KEY (covered_through_message_id) REFERENCES message (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_memory_checkpoint__prompt FOREIGN KEY (prompt_version_id) REFERENCES prompt_version (id) ON DELETE SET NULL,
    CONSTRAINT ck_conversation_memory_checkpoint__summary CHECK (JSON_TYPE(summary_json) = 'OBJECT'),
    CONSTRAINT ck_conversation_memory_checkpoint__hash CHECK (source_hash REGEXP '^[0-9a-f]{64}$'),
    INDEX idx_conversation_memory_checkpoint__branch_sequence (branch_id, covered_through_sequence DESC),
    INDEX idx_conversation_memory_checkpoint__conversation (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
