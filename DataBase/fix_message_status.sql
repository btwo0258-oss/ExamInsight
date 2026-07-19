-- The message table defines 0 as active and 1 as deleted.
-- Older chat code wrote new messages as 1. Restore only messages whose
-- conversation is still active, so deleted conversations stay deleted.
UPDATE `message` m
INNER JOIN `conversation` c ON c.id = m.conversation_id
SET m.status = 0
WHERE c.status = 0 AND m.status = 1;
