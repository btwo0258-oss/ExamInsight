-- ExamInsight V2 conversations, capability registry and AI orchestration records.
-- This migration stores user-visible messages, immutable evidence and safe runtime
-- summaries. It never stores hidden reasoning, provider credentials or raw provider
-- request/response payloads, and it never inserts capability or demo seed data.
--
-- Learning-domain pointers and circular current-object pointers remain nullable
-- columns until their target tables exist; V019 adds those foreign keys.

-- Required by ai_run so a user-owned AI run cannot reference another user's job.
ALTER TABLE async_job
    ADD CONSTRAINT uq_async_job__id_user_id UNIQUE (id, user_id);

CREATE TABLE capability_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    capability_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_mode VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    suggested_prompt_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    required_permission VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
    quota_policy_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    audience_rule_json JSON NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_capability_definition PRIMARY KEY (id),
    CONSTRAINT uq_capability_definition__external_id UNIQUE (external_id),
    CONSTRAINT uq_capability_definition__key UNIQUE (capability_key),
    CONSTRAINT ck_capability_definition__key CHECK (
        capability_key REGEXP '^[a-z][a-z0-9_.-]{0,63}$'
    ),
    CONSTRAINT ck_capability_definition__entry_mode CHECK (
        entry_mode IN ('CHAT', 'LEARNING', 'WORKSPACE')
    ),
    CONSTRAINT ck_capability_definition__status CHECK (
        status IN ('HIDDEN', 'BETA', 'AVAILABLE')
    ),
    CONSTRAINT ck_capability_definition__translation_keys CHECK (
        title_key REGEXP '^[a-z][a-z0-9_.-]{0,95}$'
        AND description_key REGEXP '^[a-z][a-z0-9_.-]{0,95}$'
        AND suggested_prompt_key REGEXP '^[a-z][a-z0-9_.-]{0,95}$'
    ),
    CONSTRAINT ck_capability_definition__permission CHECK (
        required_permission IS NULL
        OR required_permission REGEXP '^[a-z][a-z0-9_.:-]{0,95}$'
    ),
    CONSTRAINT ck_capability_definition__quota_policy CHECK (
        quota_policy_key IS NULL
        OR quota_policy_key REGEXP '^[a-z][a-z0-9_.-]{0,63}$'
    ),
    CONSTRAINT ck_capability_definition__audience_rule CHECK (
        audience_rule_json IS NULL OR JSON_TYPE(audience_rule_json) = 'OBJECT'
    ),
    CONSTRAINT ck_capability_definition__sort_order CHECK (
        sort_order <= 1000000
    ),
    INDEX idx_capability_definition__entry_status_sort (entry_mode, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    conversation_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    learning_project_id BIGINT UNSIGNED NULL,
    knowledge_base_id BIGINT UNSIGNED NULL,
    title VARCHAR(160) NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_branch_id BIGINT UNSIGNED NULL,
    last_message_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL,
    trash_started_at DATETIME(3) NULL,
    previous_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    deleted_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_conversation PRIMARY KEY (id),
    CONSTRAINT uq_conversation__external_id UNIQUE (external_id),
    CONSTRAINT uq_conversation__id_user_id UNIQUE (id, user_id),
    CONSTRAINT fk_conversation__user_id__app_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_conversation__knowledge_base_id__knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id),
    CONSTRAINT ck_conversation__type CHECK (
        conversation_type IN ('GENERAL', 'LEARNING')
    ),
    CONSTRAINT ck_conversation__type_binding CHECK (
        (conversation_type = 'GENERAL' AND learning_project_id IS NULL)
        OR (
            conversation_type = 'LEARNING'
            AND learning_project_id IS NOT NULL
            AND knowledge_base_id IS NULL
        )
    ),
    CONSTRAINT ck_conversation__title CHECK (
        CHAR_LENGTH(title) > 0
        AND title = TRIM(title)
        AND title NOT REGEXP '[[:cntrl:]]'
    ),
    CONSTRAINT ck_conversation__status CHECK (
        status IN ('ACTIVE', 'ARCHIVED', 'TRASHED', 'PURGED')
    ),
    CONSTRAINT ck_conversation__previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('ACTIVE', 'ARCHIVED')
    ),
    CONSTRAINT ck_conversation__last_message_time CHECK (
        last_message_at IS NULL OR last_message_at >= created_at
    ),
    CONSTRAINT ck_conversation__lifecycle CHECK (
        (
            status = 'ACTIVE'
            AND archived_at IS NULL
            AND trash_started_at IS NULL
            AND previous_status IS NULL
            AND deleted_at IS NULL
        )
        OR (
            status = 'ARCHIVED'
            AND archived_at IS NOT NULL
            AND archived_at >= created_at
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
            AND (
                (previous_status = 'ACTIVE' AND archived_at IS NULL)
                OR (
                    previous_status = 'ARCHIVED'
                    AND archived_at IS NOT NULL
                    AND archived_at <= trash_started_at
                )
            )
        )
    ),
    INDEX idx_conversation__user_type_status_message (user_id, conversation_type, status, last_message_at),
    INDEX idx_conversation__learning_project_status (learning_project_id, status),
    INDEX idx_conversation__knowledge_base_id (knowledge_base_id),
    INDEX idx_conversation__active_branch_id (active_branch_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE conversation_branch (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    parent_branch_id BIGINT UNSIGNED NULL,
    forked_from_message_id BIGINT UNSIGNED NULL,
    active_run_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_conversation_branch PRIMARY KEY (id),
    CONSTRAINT uq_conversation_branch__external_id UNIQUE (external_id),
    CONSTRAINT uq_conversation_branch__id_conversation UNIQUE (id, conversation_id),
    CONSTRAINT fk_conversation_branch__conversation_user
        FOREIGN KEY (conversation_id, created_by_user_id)
        REFERENCES conversation (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_branch__parent_conversation
        FOREIGN KEY (parent_branch_id, conversation_id)
        REFERENCES conversation_branch (id, conversation_id),
    CONSTRAINT ck_conversation_branch__fork_pair CHECK (
        (parent_branch_id IS NULL AND forked_from_message_id IS NULL)
        OR (parent_branch_id IS NOT NULL AND forked_from_message_id IS NOT NULL)
    ),
    CONSTRAINT ck_conversation_branch__status CHECK (
        status IN ('ACTIVE', 'ARCHIVED')
    ),
    INDEX idx_conversation_branch__conversation_created (conversation_id, created_at),
    INDEX idx_conversation_branch__parent_id (parent_branch_id),
    INDEX idx_conversation_branch__active_run_id (active_run_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    branch_id BIGINT UNSIGNED NOT NULL,
    parent_message_id BIGINT UNSIGNED NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sequence_no BIGINT UNSIGNED NOT NULL,
    plain_text LONGTEXT NULL,
    edited_from_message_id BIGINT UNSIGNED NULL,
    response_group_id BIGINT UNSIGNED NULL,
    generated_by_ai BOOLEAN NOT NULL DEFAULT FALSE,
    ai_run_id BIGINT UNSIGNED NULL,
    generation_label VARCHAR(160) NULL,
    finalized_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_message PRIMARY KEY (id),
    CONSTRAINT uq_message__external_id UNIQUE (external_id),
    CONSTRAINT uq_message__id_branch UNIQUE (id, branch_id),
    CONSTRAINT uq_message__id_conversation UNIQUE (id, conversation_id),
    CONSTRAINT uq_message__branch_sequence UNIQUE (branch_id, sequence_no),
    CONSTRAINT fk_message__branch_conversation
        FOREIGN KEY (branch_id, conversation_id)
        REFERENCES conversation_branch (id, conversation_id) ON DELETE CASCADE,
    CONSTRAINT fk_message__parent_branch
        FOREIGN KEY (parent_message_id, branch_id)
        REFERENCES message (id, branch_id),
    CONSTRAINT fk_message__edited_conversation
        FOREIGN KEY (edited_from_message_id, conversation_id)
        REFERENCES message (id, conversation_id),
    CONSTRAINT ck_message__role CHECK (
        role IN ('USER', 'ASSISTANT')
    ),
    CONSTRAINT ck_message__status CHECK (
        status IN ('STREAMING', 'FINALIZED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_message__sequence_no CHECK (
        sequence_no > 0
    ),
    CONSTRAINT ck_message__plain_text CHECK (
        plain_text IS NULL OR CHAR_LENGTH(plain_text) > 0
    ),
    CONSTRAINT ck_message__streaming_role CHECK (
        status <> 'STREAMING' OR role = 'ASSISTANT'
    ),
    CONSTRAINT ck_message__lifecycle CHECK (
        (status = 'STREAMING' AND finalized_at IS NULL)
        OR (
            status IN ('FINALIZED', 'FAILED', 'CANCELLED')
            AND finalized_at IS NOT NULL
            AND finalized_at >= created_at
        )
    ),
    CONSTRAINT ck_message__generation_metadata CHECK (
        (
            role = 'USER'
            AND generated_by_ai = FALSE
            AND ai_run_id IS NULL
            AND generation_label IS NULL
        )
        OR (
            role = 'ASSISTANT'
            AND generated_by_ai = TRUE
            AND ai_run_id IS NOT NULL
            AND generation_label IS NOT NULL
            AND CHAR_LENGTH(TRIM(generation_label)) > 0
        )
    ),
    INDEX idx_message__conversation_created (conversation_id, created_at),
    INDEX idx_message__response_group_id (response_group_id),
    INDEX idx_message__ai_run_id (ai_run_id),
    INDEX idx_message__edited_from_id (edited_from_message_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE assistant_response_group (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    branch_id BIGINT UNSIGNED NOT NULL,
    user_message_id BIGINT UNSIGNED NOT NULL,
    selected_message_id BIGINT UNSIGNED NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_assistant_response_group PRIMARY KEY (id),
    CONSTRAINT uq_response_group__external_id UNIQUE (external_id),
    CONSTRAINT uq_response_group__branch_user_message UNIQUE (branch_id, user_message_id),
    CONSTRAINT fk_response_group__branch_conversation
        FOREIGN KEY (branch_id, conversation_id)
        REFERENCES conversation_branch (id, conversation_id) ON DELETE CASCADE,
    CONSTRAINT fk_response_group__user_message_branch
        FOREIGN KEY (user_message_id, branch_id)
        REFERENCES message (id, branch_id) ON DELETE CASCADE,
    INDEX idx_response_group__conversation_id (conversation_id),
    INDEX idx_response_group__selected_message_id (selected_message_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE message_part (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    part_no INT UNSIGNED NOT NULL,
    part_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    text_content LONGTEXT NULL,
    display_json JSON NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_message_part PRIMARY KEY (id),
    CONSTRAINT uq_message_part__message_part_no UNIQUE (message_id, part_no),
    CONSTRAINT fk_message_part__message_id__message
        FOREIGN KEY (message_id) REFERENCES message (id) ON DELETE CASCADE,
    CONSTRAINT ck_message_part__part_no CHECK (
        part_no > 0
    ),
    CONSTRAINT ck_message_part__part_type CHECK (
        part_type IN ('TEXT', 'MARKDOWN', 'CODE_BLOCK', 'TABLE', 'DISPLAY_CARD')
    ),
    CONSTRAINT ck_message_part__content_shape CHECK (
        (
            part_type IN ('TEXT', 'MARKDOWN', 'CODE_BLOCK')
            AND text_content IS NOT NULL
            AND CHAR_LENGTH(text_content) > 0
            AND display_json IS NULL
        )
        OR (
            part_type IN ('TABLE', 'DISPLAY_CARD')
            AND text_content IS NULL
            AND display_json IS NOT NULL
            AND JSON_TYPE(display_json) = 'OBJECT'
        )
    ),
    CONSTRAINT ck_message_part__content_hash CHECK (
        content_sha256 REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE message_attachment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    asset_version_id BIGINT UNSIGNED NOT NULL,
    attachment_role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_message_attachment PRIMARY KEY (id),
    CONSTRAINT uq_message_attachment__message_version_role
        UNIQUE (message_id, asset_version_id, attachment_role),
    CONSTRAINT fk_message_attachment__message_id__message
        FOREIGN KEY (message_id) REFERENCES message (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_attachment__asset_version_id__asset_version
        FOREIGN KEY (asset_version_id) REFERENCES asset_version (id),
    CONSTRAINT ck_message_attachment__role CHECK (
        attachment_role IN ('CONTEXT', 'REFERENCE', 'OUTPUT')
    ),
    CONSTRAINT ck_message_attachment__display_name CHECK (
        CHAR_LENGTH(display_name) > 0
        AND display_name = TRIM(display_name)
        AND display_name NOT REGEXP '[[:cntrl:]]'
    ),
    INDEX idx_message_attachment__asset_version_id (asset_version_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE message_citation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    citation_no INT UNSIGNED NOT NULL,
    citation_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    document_chunk_id BIGINT UNSIGNED NULL,
    question_version_id BIGINT UNSIGNED NULL,
    scope_node_id BIGINT UNSIGNED NULL,
    learning_resource_version_id BIGINT UNSIGNED NULL,
    quoted_text VARCHAR(2000) NULL,
    locator_label VARCHAR(500) NULL,
    support_score DECIMAL(7,6) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_message_citation PRIMARY KEY (id),
    CONSTRAINT uq_message_citation__message_citation_no UNIQUE (message_id, citation_no),
    CONSTRAINT fk_message_citation__message_id__message
        FOREIGN KEY (message_id) REFERENCES message (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_citation__chunk_id__document_chunk
        FOREIGN KEY (document_chunk_id) REFERENCES document_chunk (id),
    CONSTRAINT ck_message_citation__citation_no CHECK (
        citation_no > 0
    ),
    CONSTRAINT ck_message_citation__target CHECK (
        (
            citation_type = 'DOCUMENT_CHUNK'
            AND document_chunk_id IS NOT NULL
            AND question_version_id IS NULL
            AND scope_node_id IS NULL
            AND learning_resource_version_id IS NULL
        )
        OR (
            citation_type = 'QUESTION'
            AND document_chunk_id IS NULL
            AND question_version_id IS NOT NULL
            AND scope_node_id IS NULL
            AND learning_resource_version_id IS NULL
        )
        OR (
            citation_type = 'SCOPE_NODE'
            AND document_chunk_id IS NULL
            AND question_version_id IS NULL
            AND scope_node_id IS NOT NULL
            AND learning_resource_version_id IS NULL
        )
        OR (
            citation_type = 'LEARNING_RESOURCE'
            AND document_chunk_id IS NULL
            AND question_version_id IS NULL
            AND scope_node_id IS NULL
            AND learning_resource_version_id IS NOT NULL
        )
    ),
    CONSTRAINT ck_message_citation__quoted_text CHECK (
        quoted_text IS NULL OR CHAR_LENGTH(TRIM(quoted_text)) > 0
    ),
    CONSTRAINT ck_message_citation__locator_label CHECK (
        locator_label IS NULL OR CHAR_LENGTH(TRIM(locator_label)) > 0
    ),
    CONSTRAINT ck_message_citation__support_score CHECK (
        support_score IS NULL OR support_score BETWEEN 0 AND 1
    ),
    INDEX idx_message_citation__document_chunk_id (document_chunk_id),
    INDEX idx_message_citation__question_version_id (question_version_id),
    INDEX idx_message_citation__scope_node_id (scope_node_id),
    INDEX idx_message_citation__resource_version_id (learning_resource_version_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE ai_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    async_job_id BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NULL,
    branch_id BIGINT UNSIGNED NULL,
    request_message_id BIGINT UNSIGNED NULL,
    response_message_id BIGINT UNSIGNED NULL,
    learning_project_id BIGINT UNSIGNED NULL,
    capability_id BIGINT UNSIGNED NULL,
    model_policy_version_id BIGINT UNSIGNED NOT NULL,
    prompt_version_id BIGINT UNSIGNED NOT NULL,
    mode VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    intent_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    side_effect_level VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ai_run PRIMARY KEY (id),
    CONSTRAINT uq_ai_run__external_id UNIQUE (external_id),
    CONSTRAINT uq_ai_run__async_job_id UNIQUE (async_job_id),
    CONSTRAINT uq_ai_run__id_user_id UNIQUE (id, user_id),
    CONSTRAINT fk_ai_run__job_user__async_job
        FOREIGN KEY (async_job_id, user_id) REFERENCES async_job (id, user_id),
    CONSTRAINT fk_ai_run__capability_id__capability_definition
        FOREIGN KEY (capability_id) REFERENCES capability_definition (id),
    CONSTRAINT fk_ai_run__policy_version_id__model_policy_version
        FOREIGN KEY (model_policy_version_id) REFERENCES model_policy_version (id),
    CONSTRAINT fk_ai_run__prompt_version_id__prompt_version
        FOREIGN KEY (prompt_version_id) REFERENCES prompt_version (id),
    CONSTRAINT ck_ai_run__mode CHECK (
        mode IN ('GENERAL_CHAT', 'LEARNING_ASSISTANT')
    ),
    CONSTRAINT ck_ai_run__mode_context CHECK (
        conversation_id IS NOT NULL
        AND branch_id IS NOT NULL
        AND request_message_id IS NOT NULL
        AND (
            (mode = 'GENERAL_CHAT' AND learning_project_id IS NULL)
            OR (mode = 'LEARNING_ASSISTANT' AND learning_project_id IS NOT NULL)
        )
    ),
    CONSTRAINT ck_ai_run__intent_key CHECK (
        intent_key REGEXP '^[a-z][a-z0-9_.-]{0,63}$'
    ),
    CONSTRAINT ck_ai_run__side_effect CHECK (
        side_effect_level IN ('NONE', 'PROPOSAL', 'CONFIRMATION_REQUIRED')
    ),
    CONSTRAINT ck_ai_run__time_order CHECK (
        (started_at IS NULL AND completed_at IS NULL)
        OR (
            started_at IS NOT NULL
            AND (completed_at IS NULL OR completed_at >= started_at)
        )
    ),
    INDEX idx_ai_run__conversation_created (conversation_id, created_at),
    INDEX idx_ai_run__learning_project_created (learning_project_id, created_at),
    INDEX idx_ai_run__branch_id (branch_id),
    INDEX idx_ai_run__request_message_id (request_message_id),
    INDEX idx_ai_run__response_message_id (response_message_id),
    INDEX idx_ai_run__user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE ai_context_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ai_run_id BIGINT UNSIGNED NOT NULL,
    context_schema_version INT UNSIGNED NOT NULL,
    exam_target_version_id BIGINT UNSIGNED NULL,
    source_set_id BIGINT UNSIGNED NULL,
    scope_version_id BIGINT UNSIGNED NULL,
    plan_version_id BIGINT UNSIGNED NULL,
    task_execution_id BIGINT UNSIGNED NULL,
    mastery_cutoff_at DATETIME(3) NULL,
    context_manifest_json JSON NOT NULL,
    context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ai_context_snapshot PRIMARY KEY (id),
    CONSTRAINT uq_ai_context_snapshot__external_id UNIQUE (external_id),
    CONSTRAINT uq_ai_context_snapshot__ai_run_id UNIQUE (ai_run_id),
    CONSTRAINT fk_ai_context_snapshot__ai_run_id__ai_run
        FOREIGN KEY (ai_run_id) REFERENCES ai_run (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_context_snapshot__schema_version CHECK (
        context_schema_version > 0
    ),
    CONSTRAINT ck_ai_context_snapshot__manifest CHECK (
        JSON_TYPE(context_manifest_json) = 'OBJECT'
    ),
    CONSTRAINT ck_ai_context_snapshot__context_hash CHECK (
        context_hash REGEXP '^[0-9a-f]{64}$'
    ),
    INDEX idx_ai_context_snapshot__source_scope (source_set_id, scope_version_id),
    INDEX idx_ai_context_snapshot__exam_target_id (exam_target_version_id),
    INDEX idx_ai_context_snapshot__plan_version_id (plan_version_id),
    INDEX idx_ai_context_snapshot__task_execution_id (task_execution_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE retrieval_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ai_run_id BIGINT UNSIGNED NOT NULL,
    query_text LONGTEXT NOT NULL,
    query_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    retrieval_mode VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    filters_json JSON NOT NULL,
    top_k INT UNSIGNED NOT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_retrieval_run PRIMARY KEY (id),
    CONSTRAINT uq_retrieval_run__external_id UNIQUE (external_id),
    CONSTRAINT fk_retrieval_run__ai_run_id__ai_run
        FOREIGN KEY (ai_run_id) REFERENCES ai_run (id) ON DELETE CASCADE,
    CONSTRAINT ck_retrieval_run__query CHECK (
        CHAR_LENGTH(TRIM(query_text)) > 0
    ),
    CONSTRAINT ck_retrieval_run__query_hash CHECK (
        query_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_retrieval_run__mode CHECK (
        retrieval_mode IN ('HYBRID', 'SEMANTIC', 'KEYWORD')
    ),
    CONSTRAINT ck_retrieval_run__filters CHECK (
        JSON_TYPE(filters_json) = 'OBJECT'
    ),
    CONSTRAINT ck_retrieval_run__top_k CHECK (
        top_k BETWEEN 1 AND 100
    ),
    CONSTRAINT ck_retrieval_run__status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_retrieval_run__lifecycle CHECK (
        (status = 'RUNNING' AND completed_at IS NULL)
        OR (
            status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
            AND completed_at IS NOT NULL
            AND completed_at >= started_at
        )
    ),
    INDEX idx_retrieval_run__ai_run_created (ai_run_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE retrieval_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    retrieval_run_id BIGINT UNSIGNED NOT NULL,
    rank_no INT UNSIGNED NOT NULL,
    document_chunk_id BIGINT UNSIGNED NOT NULL,
    semantic_score DECIMAL(7,6) NULL,
    keyword_score DECIMAL(7,6) NULL,
    rerank_score DECIMAL(7,6) NULL,
    selected_for_context BOOLEAN NOT NULL DEFAULT FALSE,
    reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_retrieval_result PRIMARY KEY (id),
    CONSTRAINT uq_retrieval_result__run_rank UNIQUE (retrieval_run_id, rank_no),
    CONSTRAINT uq_retrieval_result__run_chunk UNIQUE (retrieval_run_id, document_chunk_id),
    CONSTRAINT fk_retrieval_result__run_id__retrieval_run
        FOREIGN KEY (retrieval_run_id) REFERENCES retrieval_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_retrieval_result__chunk_id__document_chunk
        FOREIGN KEY (document_chunk_id) REFERENCES document_chunk (id),
    CONSTRAINT ck_retrieval_result__rank_no CHECK (
        rank_no > 0
    ),
    CONSTRAINT ck_retrieval_result__scores CHECK (
        (semantic_score IS NULL OR semantic_score BETWEEN 0 AND 1)
        AND (keyword_score IS NULL OR keyword_score BETWEEN 0 AND 1)
        AND (rerank_score IS NULL OR rerank_score BETWEEN 0 AND 1)
    ),
    CONSTRAINT ck_retrieval_result__has_score CHECK (
        semantic_score IS NOT NULL OR keyword_score IS NOT NULL OR rerank_score IS NOT NULL
    ),
    CONSTRAINT ck_retrieval_result__reason_code CHECK (
        reason_code IS NULL OR reason_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
    ),
    INDEX idx_retrieval_result__document_chunk_id (document_chunk_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE ai_tool_call (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ai_run_id BIGINT UNSIGNED NOT NULL,
    call_no INT UNSIGNED NOT NULL,
    tool_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tool_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    side_effect_level VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    arguments_json JSON NOT NULL,
    arguments_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_summary_json JSON NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ai_tool_call PRIMARY KEY (id),
    CONSTRAINT uq_ai_tool_call__external_id UNIQUE (external_id),
    CONSTRAINT uq_ai_tool_call__run_call_no UNIQUE (ai_run_id, call_no),
    CONSTRAINT fk_ai_tool_call__ai_run_id__ai_run
        FOREIGN KEY (ai_run_id) REFERENCES ai_run (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_tool_call__call_no CHECK (
        call_no > 0
    ),
    CONSTRAINT ck_ai_tool_call__tool_key CHECK (
        tool_key REGEXP '^[a-z][a-z0-9_.-]{0,95}$'
    ),
    CONSTRAINT ck_ai_tool_call__tool_version CHECK (
        CHAR_LENGTH(TRIM(tool_version)) > 0
    ),
    CONSTRAINT ck_ai_tool_call__side_effect CHECK (
        side_effect_level IN ('NONE', 'PROPOSAL', 'CONFIRMATION_REQUIRED')
    ),
    CONSTRAINT ck_ai_tool_call__status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_ai_tool_call__arguments CHECK (
        JSON_TYPE(arguments_json) = 'OBJECT'
    ),
    CONSTRAINT ck_ai_tool_call__arguments_hash CHECK (
        arguments_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_ai_tool_call__result_summary CHECK (
        result_summary_json IS NULL OR JSON_TYPE(result_summary_json) = 'OBJECT'
    ),
    CONSTRAINT ck_ai_tool_call__lifecycle CHECK (
        (
            status = 'PENDING'
            AND started_at IS NULL
            AND completed_at IS NULL
            AND result_summary_json IS NULL
            AND error_code IS NULL
        )
        OR (
            status = 'RUNNING'
            AND started_at IS NOT NULL
            AND completed_at IS NULL
            AND result_summary_json IS NULL
            AND error_code IS NULL
        )
        OR (
            status = 'SUCCEEDED'
            AND started_at IS NOT NULL
            AND completed_at IS NOT NULL
            AND completed_at >= started_at
            AND result_summary_json IS NOT NULL
            AND error_code IS NULL
        )
        OR (
            status = 'FAILED'
            AND started_at IS NOT NULL
            AND completed_at IS NOT NULL
            AND completed_at >= started_at
            AND result_summary_json IS NULL
            AND error_code IS NOT NULL
        )
        OR (
            status = 'CANCELLED'
            AND completed_at IS NOT NULL
            AND (started_at IS NULL OR completed_at >= started_at)
            AND result_summary_json IS NULL
            AND error_code IS NULL
        )
    ),
    INDEX idx_ai_tool_call__tool_key_created (tool_key, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE pending_action (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    ai_run_id BIGINT UNSIGNED NOT NULL,
    learning_project_id BIGINT UNSIGNED NULL,
    action_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_schema_version INT UNSIGNED NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_aggregate_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_aggregate_external_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_row_version BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quota_estimate DECIMAL(20,8) NULL,
    expires_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    executed_at DATETIME(3) NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_pending_action PRIMARY KEY (id),
    CONSTRAINT uq_pending_action__external_id UNIQUE (external_id),
    CONSTRAINT fk_pending_action__ai_run_user
        FOREIGN KEY (ai_run_id, user_id) REFERENCES ai_run (id, user_id),
    CONSTRAINT ck_pending_action__action_type CHECK (
        action_type REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
    ),
    CONSTRAINT ck_pending_action__schema_version CHECK (
        action_schema_version > 0
    ),
    CONSTRAINT ck_pending_action__payload CHECK (
        JSON_TYPE(payload_json) = 'OBJECT'
    ),
    CONSTRAINT ck_pending_action__payload_hash CHECK (
        payload_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_pending_action__base_aggregate_type CHECK (
        base_aggregate_type REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
    ),
    CONSTRAINT ck_pending_action__base_external_id CHECK (
        base_aggregate_external_id REGEXP '^[0-9A-HJKMNP-TV-Z]{26}$'
    ),
    CONSTRAINT ck_pending_action__status CHECK (
        status IN ('PROPOSED', 'CONFIRMED', 'REJECTED', 'EXPIRED', 'EXECUTING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_pending_action__quota_estimate CHECK (
        quota_estimate IS NULL OR quota_estimate >= 0
    ),
    CONSTRAINT ck_pending_action__expiry CHECK (
        expires_at > created_at
    ),
    CONSTRAINT ck_pending_action__lifecycle CHECK (
        (
            status = 'PROPOSED'
            AND confirmed_at IS NULL
            AND executed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status = 'CONFIRMED'
            AND confirmed_at IS NOT NULL
            AND confirmed_at >= created_at
            AND executed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status IN ('REJECTED', 'EXPIRED')
            AND confirmed_at IS NULL
            AND executed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status = 'EXECUTING'
            AND confirmed_at IS NOT NULL
            AND confirmed_at >= created_at
            AND executed_at IS NULL
            AND failure_code IS NULL
        )
        OR (
            status = 'SUCCEEDED'
            AND confirmed_at IS NOT NULL
            AND executed_at IS NOT NULL
            AND confirmed_at >= created_at
            AND executed_at >= confirmed_at
            AND failure_code IS NULL
        )
        OR (
            status = 'FAILED'
            AND confirmed_at IS NOT NULL
            AND executed_at IS NOT NULL
            AND confirmed_at >= created_at
            AND executed_at >= confirmed_at
            AND failure_code IS NOT NULL
        )
    ),
    INDEX idx_pending_action__user_status_expiry (user_id, status, expires_at),
    INDEX idx_pending_action__learning_project_created (learning_project_id, created_at),
    INDEX idx_pending_action__ai_run_id (ai_run_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
