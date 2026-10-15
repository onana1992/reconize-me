CREATE TABLE verification_liveness_checks (
    id              CHAR(36)     NOT NULL,
    verification_id CHAR(36)     NOT NULL,
    attempt         INT          NOT NULL,
    session_id      VARCHAR(64)  NOT NULL,
    region          VARCHAR(32)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    confidence      DOUBLE       NULL,
    billed          TINYINT(1)   NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_liveness_verification_attempt (verification_id, attempt),
    KEY idx_liveness_verification_status (verification_id, status),
    CONSTRAINT fk_liveness_verification FOREIGN KEY (verification_id) REFERENCES verifications (id)
);
