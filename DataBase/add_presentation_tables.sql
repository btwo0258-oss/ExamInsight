ALTER TABLE `document`
  ADD COLUMN `external_key` varchar(100) NULL AFTER `file_path`,
  ADD UNIQUE KEY `uk_document_external_key` (`external_key`);

CREATE TABLE IF NOT EXISTS `presentation` (
  `id` varchar(36) NOT NULL,
  `user_id` bigint NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'draft',
  `config_json` longtext NOT NULL,
  `outline_json` longtext NULL,
  `preview_json` longtext NULL,
  `provider_outline_json` longtext NULL,
  `conversation_id` bigint NULL,
  `source_message_id` varchar(100) NULL,
  `knowledge_base_id` bigint NULL,
  `project_id` bigint NULL,
  `learning_resource_id` bigint NULL,
  `active_job_id` varchar(36) NULL,
  `file_name` varchar(255) NULL,
  `file_size` bigint NULL,
  `document_id` bigint NULL,
  `resource_id` varchar(64) NULL,
  `error_code` varchar(100) NULL,
  `error_message` text NULL,
  `client_request_id` varchar(100) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_presentation_request` (`user_id`, `client_request_id`),
  KEY `idx_presentation_user` (`user_id`),
  KEY `idx_presentation_status` (`status`),
  KEY `idx_presentation_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `presentation_job` (
  `job_id` varchar(36) NOT NULL,
  `user_id` bigint NOT NULL,
  `presentation_id` varchar(36) NOT NULL,
  `type` varchar(24) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'pending',
  `progress` int NOT NULL DEFAULT 0,
  `result_json` longtext NULL,
  `provider_sid` varchar(100) NULL,
  `last_provider_poll_at` datetime NULL,
  `error_code` varchar(100) NULL,
  `error_message` text NULL,
  `client_request_id` varchar(100) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`job_id`),
  UNIQUE KEY `uk_presentation_job_request` (`user_id`, `type`, `client_request_id`),
  KEY `idx_presentation_job_owner` (`user_id`, `presentation_id`),
  KEY `idx_presentation_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
