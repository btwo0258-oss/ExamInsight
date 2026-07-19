USE LLM;

DROP PROCEDURE IF EXISTS add_learning_project_column;
DELIMITER //
CREATE PROCEDURE add_learning_project_column(
  IN target_column VARCHAR(64),
  IN column_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'learning_project'
      AND COLUMN_NAME = target_column
  ) THEN
    SET @ddl = CONCAT(
      'ALTER TABLE learning_project ADD COLUMN `',
      REPLACE(target_column, '`', '``'),
      '` ',
      column_definition
    );
    PREPARE statement FROM @ddl;
    EXECUTE statement;
    DEALLOCATE PREPARE statement;
  END IF;
END//
DELIMITER ;

CALL add_learning_project_column('knowledge_base_id', 'BIGINT NULL AFTER library_id');
CALL add_learning_project_column('knowledge_base_name', 'VARCHAR(255) NULL AFTER knowledge_base_id');
CALL add_learning_project_column('icon', 'VARCHAR(64) NULL AFTER title');
CALL add_learning_project_column('icon_color', 'VARCHAR(32) NULL AFTER icon');
CALL add_learning_project_column('target_type', 'VARCHAR(100) NULL AFTER goal');
CALL add_learning_project_column('period', 'VARCHAR(100) NULL AFTER target_type');
CALL add_learning_project_column('daily_time', 'VARCHAR(100) NULL AFTER period');
CALL add_learning_project_column('weak_points', 'TEXT NULL AFTER daily_time');
CALL add_learning_project_column('preferences', 'JSON NULL AFTER weak_points');
CALL add_learning_project_column('payload_json', 'LONGTEXT NULL AFTER preferences');
CALL add_learning_project_column('setup_state_json', 'LONGTEXT NULL AFTER payload_json');
CALL add_learning_project_column('active_generation_json', 'LONGTEXT NULL AFTER setup_state_json');
CALL add_learning_project_column('exercise_drafts_json', 'LONGTEXT NULL AFTER active_generation_json');

DROP PROCEDURE add_learning_project_column;
