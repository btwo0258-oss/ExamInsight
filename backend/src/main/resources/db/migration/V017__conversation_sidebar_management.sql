-- Conversation-management fields for the V2 chat sidebar.
-- Existing conversations keep their current title as user-managed content.
ALTER TABLE conversation
    ADD COLUMN title_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL' AFTER title,
    ADD COLUMN pinned_at DATETIME(3) NULL AFTER active_branch_id,
    ADD CONSTRAINT ck_conversation__title_source CHECK (title_source IN ('AUTO', 'AI', 'FALLBACK', 'MANUAL')),
    ADD INDEX idx_conversation__user_sidebar_order (
        user_id, conversation_type, status, pinned_at, last_message_at, external_id
    );
