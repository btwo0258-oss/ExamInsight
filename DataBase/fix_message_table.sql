USE LLM;

ALTER TABLE `message` ADD COLUMN `kind` varchar(50) NULL DEFAULT NULL AFTER `files`;
ALTER TABLE `message` ADD COLUMN `learning_data` text NULL AFTER `kind`;
ALTER TABLE `message` ADD COLUMN `presentation_data` text NULL AFTER `learning_data`;
ALTER TABLE `message` ADD COLUMN `spreadsheet_data` text NULL AFTER `presentation_data`;
ALTER TABLE `message` ADD COLUMN `artifacts` text NULL AFTER `spreadsheet_data`;
