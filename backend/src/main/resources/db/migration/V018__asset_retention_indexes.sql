-- Retention scans are bounded by status and trash start time.  Keep these
-- indexes separate from the user-facing pagination indexes so the scheduler
-- does not turn a growing recycle bin into a full-table scan.
CREATE INDEX idx_asset__status_trash_started
    ON asset (status, trash_started_at, id);

CREATE INDEX idx_knowledge_base__status_trash_started
    ON knowledge_base (status, trash_started_at, id);
