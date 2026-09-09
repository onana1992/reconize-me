ALTER TABLE memberships
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active',
    ADD COLUMN disabled_at DATETIME(6) NULL,
    ADD COLUMN disabled_by_user_id CHAR(36) NULL;

CREATE INDEX idx_memberships_org_status ON memberships (organization_id, status);
