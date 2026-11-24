CREATE TABLE authentication_liveness_checks (
    id                  CHAR(36)     NOT NULL,
    authentication_id   CHAR(36)     NOT NULL,
    attempt             INT          NOT NULL,
    session_id          VARCHAR(64)  NOT NULL,
    region              VARCHAR(32)  NOT NULL,
    expires_at          TIMESTAMP(6) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    confidence          DOUBLE PRECISION NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_authentication_liveness_attempt UNIQUE (authentication_id, attempt),
    CONSTRAINT fk_authentication_liveness_auth FOREIGN KEY (authentication_id) REFERENCES authentications (id)
);

CREATE INDEX idx_authentication_liveness_auth ON authentication_liveness_checks (authentication_id, status);

CREATE TABLE authentication_media (
    id                  CHAR(36)     NOT NULL,
    authentication_id   CHAR(36)     NOT NULL,
    kind                VARCHAR(32)  NOT NULL,
    attempt             INT          NOT NULL,
    object_key          VARCHAR(512) NOT NULL,
    content_type        VARCHAR(128) NULL,
    byte_size           BIGINT       NULL,
    status              VARCHAR(32)  NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_authentication_media_auth FOREIGN KEY (authentication_id) REFERENCES authentications (id)
);

CREATE INDEX idx_authentication_media_auth ON authentication_media (authentication_id, kind, status, attempt);
