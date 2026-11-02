-- Historical account IDs for structured audit snapshots; existing audit rows remain intact.
-- Actor, action, target, reason and occurrence time keep their existing columns.
ALTER TABLE audit_log ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
