CREATE TABLE users (
    id                  CHAR(36)     NOT NULL,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    email_verified_at   DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
);

CREATE TABLE memberships (
    user_id             CHAR(36)     NOT NULL,
    organization_id     CHAR(36)     NOT NULL,
    role                VARCHAR(16)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id, organization_id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_memberships_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_memberships_org ON memberships (organization_id);

CREATE TABLE email_verification_tokens (
    id           CHAR(36)     NOT NULL,
    user_id      CHAR(36)     NOT NULL,
    token_hash   CHAR(64)     NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    consumed_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_email_verify_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_email_verify_hash ON email_verification_tokens (token_hash);
CREATE INDEX idx_email_verify_user ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id           CHAR(36)     NOT NULL,
    user_id      CHAR(36)     NOT NULL,
    token_hash   CHAR(64)     NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    consumed_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uq_password_reset_hash ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);

CREATE TABLE membership_invites (
    id               CHAR(36)     NOT NULL,
    organization_id  CHAR(36)     NOT NULL,
    email            VARCHAR(255) NOT NULL,
    role             VARCHAR(16)  NOT NULL,
    token_hash       CHAR(64)     NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    accepted_at      DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invites_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE UNIQUE INDEX uq_invites_hash ON membership_invites (token_hash);
CREATE INDEX idx_invites_org ON membership_invites (organization_id);

ALTER TABLE api_keys
    ADD COLUMN created_by_user_id CHAR(36) NULL,
    ADD CONSTRAINT fk_api_keys_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id);
