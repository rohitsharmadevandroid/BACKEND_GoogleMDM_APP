-- Soft-delete flag for admin users. A hard DELETE isn't viable here anyway:
-- policies.created_by, commands.issued_by, enrollment_tokens.created_by,
-- and policy_revisions.changed_by all reference admin_users(id) with no
-- ON DELETE clause (defaults to RESTRICT) - physically deleting a
-- referenced admin would fail outright, on top of destroying audit trail.
ALTER TABLE admin_users
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
