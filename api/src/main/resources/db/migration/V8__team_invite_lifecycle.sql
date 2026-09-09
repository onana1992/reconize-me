ALTER TABLE membership_invites
    ADD COLUMN cancelled_at DATETIME(6) NULL,
    ADD COLUMN pending_key VARCHAR(255) NULL;

UPDATE membership_invites
    SET pending_key = email
    WHERE accepted_at IS NULL;

CREATE UNIQUE INDEX uq_invites_pending ON membership_invites (organization_id, pending_key);
