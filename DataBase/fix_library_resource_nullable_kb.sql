-- Align library resources with the frontend/backend contract:
-- uploaded resources may exist without belonging to a knowledge base.
ALTER TABLE `document_chunk`
  DROP FOREIGN KEY `fk_chunk_kb`;

ALTER TABLE `document`
  DROP FOREIGN KEY `fk_doc_kb`;

ALTER TABLE `document`
  MODIFY COLUMN `kb_id` bigint NULL DEFAULT NULL COMMENT 'Knowledge base ID; nullable for library-only resources';

ALTER TABLE `document`
  ADD CONSTRAINT `fk_doc_kb`
  FOREIGN KEY (`kb_id`) REFERENCES `knowledge_base` (`id`)
  ON DELETE SET NULL ON UPDATE RESTRICT;

ALTER TABLE `document_chunk`
  ADD CONSTRAINT `fk_chunk_kb`
  FOREIGN KEY (`kb_id`) REFERENCES `knowledge_base` (`id`)
  ON DELETE RESTRICT ON UPDATE RESTRICT;
