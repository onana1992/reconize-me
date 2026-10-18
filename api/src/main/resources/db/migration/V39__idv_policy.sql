CREATE TABLE idv_policies (
    id                      CHAR(36)      NOT NULL,
    organization_id         CHAR(36)      NOT NULL,
    version                 INT           NOT NULL,
    active                  BOOLEAN       NOT NULL,
    active_marker           CHAR(36)      NULL,
    expired_outcome         VARCHAR(16)   NOT NULL,
    attempts_outcome        VARCHAR(16)   NOT NULL,
    ocr_review_at           DECIMAL(4, 3) NOT NULL,
    liveness_pass_at        DECIMAL(4, 3) NOT NULL,
    face_match_fail_at      DECIMAL(4, 3) NOT NULL,
    face_match_pass_at      DECIMAL(4, 3) NOT NULL,
    link_ttl_seconds        INT           NOT NULL,
    max_capture_attempts    INT           NOT NULL,
    max_liveness_attempts   INT           NOT NULL,
    published_at            DATETIME(6)   NOT NULL,
    published_by            CHAR(36)      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_idv_policies_org_version (organization_id, version),
    UNIQUE KEY uq_idv_policies_active (active_marker),
    KEY idx_idv_policies_org (organization_id),
    CONSTRAINT fk_idv_policies_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE idv_policy_documents (
    policy_id       CHAR(36)     NOT NULL,
    definition_code VARCHAR(64)  NOT NULL,
    PRIMARY KEY (policy_id, definition_code),
    CONSTRAINT fk_idv_policy_documents_policy FOREIGN KEY (policy_id) REFERENCES idv_policies (id)
);

ALTER TABLE verifications
    ADD COLUMN policy_id CHAR(36) NULL,
    ADD COLUMN policy_version INT NULL,
    ADD CONSTRAINT fk_verifications_policy FOREIGN KEY (policy_id) REFERENCES idv_policies (id);
