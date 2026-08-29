-- Project navigation state used by the smart-learning sidebar.

ALTER TABLE smart_learning_project
    ADD COLUMN pinned_at DATETIME(3) NULL AFTER row_version,
    ADD INDEX idx_smart_learning_project__user_pinned (user_id, pinned_at, updated_at);
