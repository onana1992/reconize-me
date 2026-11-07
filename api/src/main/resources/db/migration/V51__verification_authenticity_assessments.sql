CREATE TABLE verification_authenticity_assessments (
    verification_id       CHAR(36)     NOT NULL,
    side                  VARCHAR(16)  NOT NULL,
    engine_version        VARCHAR(32)  NOT NULL,
    fusion_version        VARCHAR(32)  NOT NULL,
    forensic_usable       BOOLEAN      NOT NULL,
    authenticity_score    DOUBLE PRECISION NULL,
    tampering_score       DOUBLE PRECISION NULL,
    presentation_score    DOUBLE PRECISION NULL,
    alerts_json           TEXT         NULL,
    duration_ms           BIGINT       NOT NULL,
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (verification_id, side),
    CONSTRAINT fk_verification_authenticity_assessments_verification
        FOREIGN KEY (verification_id) REFERENCES verifications (id)
);
