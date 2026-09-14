CREATE TABLE integrations (
    id               CHAR(36)     NOT NULL,
    organization_id  CHAR(36)     NOT NULL,
    product          VARCHAR(32)  NOT NULL,
    mode             VARCHAR(8)   NOT NULL,
    name             VARCHAR(128) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_integrations_org (organization_id),
    KEY idx_integrations_org_mode (organization_id, product, mode),
    CONSTRAINT fk_integrations_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

INSERT INTO integrations (id, organization_id, product, mode, name, created_at)
SELECT LOWER(UUID()), id, 'identity', 'test', 'Test', created_at
FROM organizations;

ALTER TABLE api_keys
    ADD COLUMN integration_id CHAR(36) NULL AFTER organization_id;

UPDATE api_keys k
    INNER JOIN integrations i
        ON i.organization_id = k.organization_id
       AND i.product = 'identity'
       AND i.mode = 'test'
SET k.integration_id = i.id;

ALTER TABLE api_keys
    MODIFY integration_id CHAR(36) NOT NULL,
    ADD KEY idx_api_keys_integration (integration_id),
    ADD CONSTRAINT fk_api_keys_integration FOREIGN KEY (integration_id) REFERENCES integrations (id);

ALTER TABLE verifications
    ADD COLUMN integration_id CHAR(36) NULL AFTER organization_id;

UPDATE verifications v
    INNER JOIN integrations i
        ON i.organization_id = v.organization_id
       AND i.product = 'identity'
       AND i.mode = 'test'
SET v.integration_id = i.id;

ALTER TABLE verifications
    MODIFY integration_id CHAR(36) NOT NULL,
    ADD KEY idx_verifications_integration (integration_id),
    ADD CONSTRAINT fk_verifications_integration FOREIGN KEY (integration_id) REFERENCES integrations (id);
