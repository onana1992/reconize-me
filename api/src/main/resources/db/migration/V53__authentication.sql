CREATE TABLE enrollments (
    id                      CHAR(36)     NOT NULL,
    organization_id         CHAR(36)     NOT NULL,
    external_id             VARCHAR(128) NOT NULL,
    status                  VARCHAR(16)  NOT NULL,
    source                  VARCHAR(16)  NOT NULL,
    source_verification_id  CHAR(36)     NOT NULL,
    face_id                 VARCHAR(128) NULL,
    collection_id           VARCHAR(128) NULL,
    active_marker           VARCHAR(200) NULL,
    created_at              TIMESTAMP(6) NOT NULL,
    ended_at                TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_enrollments_active UNIQUE (active_marker),
    CONSTRAINT fk_enrollments_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_enrollments_org_external ON enrollments (organization_id, external_id, created_at);

CREATE TABLE authentications (
    id                  CHAR(36)     NOT NULL,
    organization_id     CHAR(36)     NOT NULL,
    integration_id      CHAR(36)     NOT NULL,
    external_id         VARCHAR(128) NOT NULL,
    enrollment_id       CHAR(36)     NOT NULL,
    face_id             VARCHAR(128) NOT NULL,
    engine              VARCHAR(16)  NOT NULL,
    policy_version      VARCHAR(32)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    reasons             VARCHAR(256) NULL,
    failure_reason      VARCHAR(64)  NULL,
    match_score         DECIMAL(4, 3) NULL,
    metadata            TEXT         NULL,
    consent_version     VARCHAR(32)  NULL,
    consent_at          TIMESTAMP(6) NULL,
    hosted_token_hash   CHAR(64)     NOT NULL,
    expires_at          TIMESTAMP(6) NOT NULL,
    attempts            INT          NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    decided_at          TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_authentications_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_authentications_integration FOREIGN KEY (integration_id) REFERENCES integrations (id),
    CONSTRAINT fk_authentications_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id)
);

CREATE INDEX idx_authentications_integration ON authentications (integration_id, created_at, id);
CREATE INDEX idx_authentications_external ON authentications (organization_id, external_id);

CREATE TABLE authentication_policies (
    id                   CHAR(36)      NOT NULL,
    organization_id      CHAR(36)      NOT NULL,
    integration_id       CHAR(36)      NULL,
    version              INT           NOT NULL,
    active               BOOLEAN       NOT NULL,
    active_marker        CHAR(36)      NULL,
    approval_threshold   DECIMAL(4, 3) NOT NULL,
    review_threshold     DECIMAL(4, 3) NOT NULL,
    on_borderline        VARCHAR(16)   NOT NULL,
    max_capture_attempts INT           NOT NULL,
    published_at         TIMESTAMP(6)  NOT NULL,
    published_by         CHAR(36)      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_authentication_policies_integration_version UNIQUE (integration_id, version),
    CONSTRAINT uq_authentication_policies_active UNIQUE (active_marker),
    CONSTRAINT fk_authentication_policies_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE authentication_idempotency_keys (
    organization_id    CHAR(36)    NOT NULL,
    idempotency_key    VARCHAR(64) NOT NULL,
    request_hash       CHAR(64)    NOT NULL,
    authentication_id  CHAR(36)    NOT NULL,
    created_at         TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (organization_id, idempotency_key)
);
