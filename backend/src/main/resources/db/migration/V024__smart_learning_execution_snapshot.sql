ALTER TABLE smart_learning_execution
    ADD COLUMN question_snapshot_json JSON NULL AFTER answers_json,
    ADD COLUMN grading_json JSON NULL AFTER score;
