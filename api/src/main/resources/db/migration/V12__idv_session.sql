CREATE TABLE verifications (
    id                      CHAR(36)     NOT NULL,
    organization_id         CHAR(36)     NOT NULL,
    external_id             VARCHAR(128) NULL,
    status                  VARCHAR(32)  NOT NULL,
    applicant_first_name    VARCHAR(128) NULL,
    applicant_last_name     VARCHAR(128) NULL,
    applicant_email         VARCHAR(320) NULL,
    metadata                TEXT         NULL,
    hosted_token_hash       CHAR(64)     NOT NULL,
    hosted_expires_at       DATETIME(6)  NOT NULL,
    decision                VARCHAR(32)  NULL,
    decision_reasons        TEXT         NULL,
    rules_version           VARCHAR(32)  NULL,
    sandbox_scenario        VARCHAR(64)  NULL,
    extracted_identity      TEXT         NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_verifications_hosted_token_hash (hosted_token_hash),
    UNIQUE KEY uq_verifications_org_external (organization_id, external_id),
    KEY idx_verifications_org_created (organization_id, created_at),
    KEY idx_verifications_org_status (organization_id, status),
    CONSTRAINT fk_verifications_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE consents (
    id              CHAR(36)     NOT NULL,
    verification_id CHAR(36)     NOT NULL,
    decision        VARCHAR(16)  NOT NULL,
    text_version    VARCHAR(64)  NOT NULL,
    accepted_at     DATETIME(6)  NOT NULL,
    ip_hash         CHAR(64)     NULL,
    user_agent      VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_consents_verification (verification_id),
    CONSTRAINT fk_consents_verification FOREIGN KEY (verification_id) REFERENCES verifications (id)
);

CREATE TABLE idempotency_keys (
    organization_id CHAR(36)    NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    verification_id CHAR(36)    NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (organization_id, idempotency_key),
    CONSTRAINT fk_idempotency_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE verification_media (
    id              CHAR(36)     NOT NULL,
    verification_id CHAR(36)     NOT NULL,
    kind            VARCHAR(32)  NOT NULL,
    attempt         INT          NOT NULL,
    object_key      VARCHAR(512) NOT NULL,
    content_type    VARCHAR(64)  NULL,
    byte_size       BIGINT       NULL,
    status          VARCHAR(32)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_media_verification_kind_attempt (verification_id, kind, attempt),
    CONSTRAINT fk_media_verification FOREIGN KEY (verification_id) REFERENCES verifications (id)
);

CREATE TABLE verification_signals (
    id              CHAR(36)     NOT NULL,
    verification_id CHAR(36)     NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    outcome         VARCHAR(32)  NOT NULL,
    score           DOUBLE       NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_signals_verification (verification_id),
    CONSTRAINT fk_signals_verification FOREIGN KEY (verification_id) REFERENCES verifications (id)
);
