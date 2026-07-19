-- 修复数据库表结构缺失的字段
-- 执行时间: 2026-07-18
-- 注意: 如果字段已存在会报错，忽略即可

-- ============================================
-- 1. 修复 message 表缺失的字段
-- ============================================

-- 添加 kind 字段
ALTER TABLE `message` 
ADD COLUMN `kind` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '消息类型：learning-profile/learning-document/presentation/spreadsheet' AFTER `files`;

-- 添加 learning_data 字段
ALTER TABLE `message` 
ADD COLUMN `learning_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '学习资料JSON数据' AFTER `kind`;

-- 添加 presentation_data 字段
ALTER TABLE `message` 
ADD COLUMN `presentation_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'PPT生成数据JSON' AFTER `learning_data`;

-- 添加 spreadsheet_data 字段
ALTER TABLE `message` 
ADD COLUMN `spreadsheet_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '电子表格生成数据JSON' AFTER `presentation_data`;

-- 添加 artifacts 字段
ALTER TABLE `message` 
ADD COLUMN `artifacts` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '聊天产物JSON数组' AFTER `spreadsheet_data`;
