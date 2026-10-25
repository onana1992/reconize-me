CREATE TABLE aml_policies (
    id                    CHAR(36)      NOT NULL,
    organization_id       CHAR(36)      NOT NULL,
    version               INT           NOT NULL,
    active                BOOLEAN       NOT NULL,
    active_marker         CHAR(36)      NULL,
    review_threshold      DECIMAL(4, 3) NOT NULL,
    confirm_threshold     DECIMAL(4, 3) NOT NULL,
    categories            VARCHAR(128)  NOT NULL,
    attach_to_identity    BOOLEAN       NOT NULL,
    on_match              VARCHAR(16)   NOT NULL,
    test_integration_id   CHAR(36)      NULL,
    live_integration_id   CHAR(36)      NULL,
    published_at          DATETIME(6)   NOT NULL,
    published_by          CHAR(36)      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_aml_policies_org_version (organization_id, version),
    UNIQUE KEY uq_aml_policies_active (active_marker),
    CONSTRAINT fk_aml_policies_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE screenings (
    id                     CHAR(36)     NOT NULL,
    organization_id        CHAR(36)     NOT NULL,
    integration_id         CHAR(36)     NOT NULL,
    external_id            VARCHAR(128) NULL,
    verification_id        CHAR(36)     NULL,
    status                 VARCHAR(16)  NOT NULL,
    outcome                VARCHAR(32)  NULL,
    failure_reason         VARCHAR(64)  NULL,
    engine                 VARCHAR(16)  NOT NULL,
    policy_version         VARCHAR(32)  NOT NULL,
    corpus_version         VARCHAR(32)  NOT NULL,
    retrieved_at           DATETIME(6)  NULL,
    subject_type           VARCHAR(16)  NOT NULL,
    first_name             VARCHAR(128) NOT NULL,
    last_name              VARCHAR(128) NOT NULL,
    middle_name            VARCHAR(128) NULL,
    date_of_birth          DATE         NULL,
    nationality            CHAR(2)      NULL,
    country_of_residence   CHAR(2)      NULL,
    id_number              VARCHAR(64)  NULL,
    id_type                VARCHAR(32)  NULL,
    normalized_subject     VARCHAR(512) NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_screenings_integration (integration_id, created_at, id),
    KEY idx_screenings_subject (organization_id, normalized_subject),
    CONSTRAINT fk_screenings_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_screenings_integration FOREIGN KEY (integration_id) REFERENCES integrations (id)
);

CREATE TABLE screening_hits (
    id                   CHAR(36)     NOT NULL,
    screening_id         CHAR(36)     NOT NULL,
    entity_id            VARCHAR(128) NOT NULL,
    category             VARCHAR(32)  NOT NULL,
    categories           VARCHAR(128) NOT NULL,
    score                DECIMAL(4, 3) NOT NULL,
    matched_name         VARCHAR(256) NOT NULL,
    listed_name          VARCHAR(256) NOT NULL,
    aliases_json         TEXT         NULL,
    birth_dates_json     TEXT         NULL,
    countries_json       TEXT         NULL,
    datasets_json        TEXT         NULL,
    features_json        TEXT         NULL,
    provider_features_json TEXT       NULL,
    disposition          VARCHAR(32)  NOT NULL,
    disposition_reason   VARCHAR(64)  NULL,
    disposition_note     VARCHAR(500) NULL,
    prior_disposition    VARCHAR(32)  NULL,
    prior_screening_id   CHAR(36)     NULL,
    PRIMARY KEY (id),
    KEY idx_screening_hits_screening (screening_id),
    CONSTRAINT fk_screening_hits_screening FOREIGN KEY (screening_id) REFERENCES screenings (id)
);

CREATE TABLE screening_idempotency_keys (
    organization_id  CHAR(36)    NOT NULL,
    idempotency_key  VARCHAR(64) NOT NULL,
    request_hash     CHAR(64)    NOT NULL,
    screening_id     CHAR(36)    NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (organization_id, idempotency_key)
);

ALTER TABLE verifications
    ADD COLUMN aml_screening_id CHAR(36) NULL;
