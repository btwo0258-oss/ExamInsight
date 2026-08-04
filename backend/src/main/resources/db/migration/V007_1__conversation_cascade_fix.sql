-- Forward-only correction for V007 self-referencing conversation records.
-- Conversation and branch deletion must remove the whole private conversation
-- tree instead of being blocked by the parent pointers inside that same tree.

ALTER TABLE conversation_branch
    DROP FOREIGN KEY fk_conversation_branch__parent_conversation;

ALTER TABLE conversation_branch
    ADD CONSTRAINT fk_conversation_branch__parent_conversation
        FOREIGN KEY (parent_branch_id, conversation_id)
        REFERENCES conversation_branch (id, conversation_id) ON DELETE CASCADE;

ALTER TABLE message
    DROP FOREIGN KEY fk_message__parent_branch;

ALTER TABLE message
    DROP FOREIGN KEY fk_message__edited_conversation;

ALTER TABLE message
    ADD CONSTRAINT fk_message__parent_branch
        FOREIGN KEY (parent_message_id, branch_id)
        REFERENCES message (id, branch_id) ON DELETE CASCADE;

ALTER TABLE message
    ADD CONSTRAINT fk_message__edited_conversation
        FOREIGN KEY (edited_from_message_id, conversation_id)
        REFERENCES message (id, conversation_id) ON DELETE CASCADE;
