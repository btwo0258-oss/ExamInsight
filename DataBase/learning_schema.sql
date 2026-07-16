-- 智能学习模块数据库表结构

-- 学习项目表
CREATE TABLE IF NOT EXISTS `learning_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `library_id` BIGINT DEFAULT NULL,
  `title` VARCHAR(255) NOT NULL,
  `goal` TEXT,
  `profile` JSON,
  `status` VARCHAR(50) NOT NULL DEFAULT 'draft',
  `total_tasks` INT DEFAULT 0,
  `completed_tasks` INT DEFAULT 0,
  `progress` INT DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_library_id` (`library_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 学习阶段表
CREATE TABLE IF NOT EXISTS `learning_stage` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `stage_order` INT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `status` VARCHAR(50) NOT NULL DEFAULT 'not_started',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_stage_order` (`project_id`, `stage_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 学习任务表
CREATE TABLE IF NOT EXISTS `learning_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `stage_id` BIGINT NOT NULL,
  `project_id` BIGINT NOT NULL,
  `task_order` INT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `status` VARCHAR(50) NOT NULL DEFAULT 'not_started',
  `read_progress` INT DEFAULT 0,
  `valid_study_seconds` INT DEFAULT 0,
  `completion_mode` VARCHAR(50),
  `exercise_ids` JSON,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stage_id` (`stage_id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_task_order` (`stage_id`, `task_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 学习练习题表
CREATE TABLE IF NOT EXISTS `learning_exercise` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `task_id` BIGINT NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `content` TEXT NOT NULL,
  `options` JSON,
  `answer` TEXT NOT NULL,
  `explanation` TEXT,
  `difficulty` VARCHAR(50),
  `cognitive_level` VARCHAR(50),
  `knowledge_points` JSON,
  `scene` VARCHAR(50),
  `purpose` VARCHAR(50),
  `code_languages` JSON,
  `generation_batch` VARCHAR(100),
  `submitted` TINYINT DEFAULT 0,
  `user_answer` TEXT,
  `grading_correct` TINYINT,
  `grading_score` INT,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 错题表
CREATE TABLE IF NOT EXISTS `learning_mistake` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `exercise_id` BIGINT NOT NULL,
  `status` VARCHAR(50) NOT NULL DEFAULT 'needs_review',
  `error_count` INT DEFAULT 1,
  `review_count` INT DEFAULT 0,
  `correct_streak` INT DEFAULT 0,
  `review_history` JSON,
  `last_wrong_at` DATETIME,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_exercise_id` (`exercise_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 学习资源表
CREATE TABLE IF NOT EXISTS `learning_resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `stage_id` BIGINT,
  `task_id` BIGINT,
  `group_type` VARCHAR(50) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `status` VARCHAR(50) NOT NULL DEFAULT 'not_selected',
  `error_message` TEXT,
  `file_name` VARCHAR(255),
  `file_path` VARCHAR(500),
  `content` LONGTEXT,
  `action` VARCHAR(50),
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_stage_id` (`stage_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 异步任务表
CREATE TABLE IF NOT EXISTS `generation_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `job_id` VARCHAR(100) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `project_id` BIGINT,
  `type` VARCHAR(50) NOT NULL,
  `status` VARCHAR(50) NOT NULL DEFAULT 'pending',
  `progress` INT DEFAULT 0,
  `result` JSON,
  `error_code` VARCHAR(100),
  `error_message` TEXT,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 学习行为记录表
CREATE TABLE IF NOT EXISTS `learning_activity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `task_id` BIGINT NOT NULL,
  `event_type` VARCHAR(50) NOT NULL,
  `progress` INT,
  `seconds_delta` INT,
  `action` VARCHAR(100),
  `client_request_id` VARCHAR(100),
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
