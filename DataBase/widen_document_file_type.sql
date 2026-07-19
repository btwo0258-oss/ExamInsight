-- Generated resources may store a full MIME type instead of a short extension.
ALTER TABLE `document`
  MODIFY COLUMN `file_type` varchar(255) CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件扩展名或 MIME 类型';
