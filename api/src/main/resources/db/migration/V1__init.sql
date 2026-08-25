CREATE TABLE organizations (
    id              CHAR(36)     NOT NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(64)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_organizations_slug (slug)
);

CREATE TABLE api_keys (
    id               CHAR(36)     NOT NULL,
    organization_id  CHAR(36)     NOT NULL,
    key_prefix       VARCHAR(16)  NOT NULL,
    key_hash         VARCHAR(255) NOT NULL,
    revoked          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_api_keys_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_api_keys_org ON api_keys (organization_id);
CREATE INDEX idx_api_keys_prefix ON api_keys (key_prefix, revoked);

CREATE TABLE verifications (
    id                    CHAR(36)     NOT NULL,
    organization_id       CHAR(36)     NOT NULL,
    external_id           VARCHAR(128),
    status                VARCHAR(32)  NOT NULL,
    applicant_first_name  VARCHAR(100),
    applicant_last_name   VARCHAR(100),
    applicant_email       VARCHAR(255),
    hosted_token_hash     CHAR(64)     NOT NULL,
    hosted_expires_at     DATETIME(6)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_verifications_hosted_token (hosted_token_hash),
    UNIQUE KEY uq_verifications_org_external (organization_id, external_id),
    CONSTRAINT fk_verifications_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_verifications_org_created ON verifications (organization_id, created_at);
CREATE INDEX idx_verifications_org_status ON verifications (organization_id, status);

CREATE TABLE consents (
    id               CHAR(36)     NOT NULL,
    organization_id  CHAR(36)     NOT NULL,
    verification_id  CHAR(36)     NOT NULL,
    decision         VARCHAR(16)  NOT NULL,
    text_version     VARCHAR(32)  NOT NULL,
    accepted_at      DATETIME(6),
    ip_hash          CHAR(64),
    user_agent       VARCHAR(512),
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_consents_verification (verification_id),
    CONSTRAINT fk_consents_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_consents_verification FOREIGN KEY (verification_id) REFERENCES verifications (id)
);

CREATE TABLE audit_events (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    organization_id  CHAR(36)     NOT NULL,
    actor_type       VARCHAR(32)  NOT NULL,
    actor_id         CHAR(36),
    action           VARCHAR(64)  NOT NULL,
    resource_type    VARCHAR(32)  NOT NULL,
    resource_id      CHAR(36)     NOT NULL,
    payload          TEXT         NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_audit_org_created ON audit_events (organization_id, created_at);
CREATE INDEX idx_audit_resource ON audit_events (resource_type, resource_id);

CREATE TABLE idempotency_keys (
    organization_id  CHAR(36)     NOT NULL,
    `key`            VARCHAR(64)  NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    verification_id  CHAR(36),
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (organization_id, `key`)
);
