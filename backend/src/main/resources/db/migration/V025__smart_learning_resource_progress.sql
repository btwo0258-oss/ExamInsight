-- Keep resource preparation observable without storing generated content in
-- transient job payloads.  The stage is deliberately coarse and reflects
-- real backend transitions rather than an estimated model percentage.

ALTER TABLE smart_learning_resource
    ADD COLUMN generation_stage VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'QUEUED' AFTER status,
    ADD COLUMN generation_progress TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER generation_stage;

UPDATE smart_learning_resource
SET generation_stage = CASE status
        WHEN 'READY' THEN 'READY'
        WHEN 'FAILED' THEN 'FAILED'
        WHEN 'GENERATING' THEN 'GENERATING_CONTENT'
        ELSE 'QUEUED'
    END,
    generation_progress = CASE status
        WHEN 'READY' THEN 100
        WHEN 'GENERATING' THEN 35
        ELSE 0
    END;

ALTER TABLE smart_learning_resource
    ADD CONSTRAINT ck_smart_learning_resource__generation_progress
        CHECK (generation_progress <= 100);
