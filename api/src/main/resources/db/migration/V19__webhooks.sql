-- M5 client webhooks (one endpoint per integration)

CREATE TABLE webhook_endpoints (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NOT NULL,
    integration_id CHAR(36) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    secret_cipher VARCHAR(512) NOT NULL,
    secret_prefix VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_webhook_endpoints_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_webhook_endpoints_integration FOREIGN KEY (integration_id) REFERENCES integrations (id),
    CONSTRAINT uq_webhook_endpoints_integration UNIQUE (integration_id)
);

CREATE INDEX idx_webhook_endpoints_org ON webhook_endpoints (organization_id);

CREATE TABLE webhook_deliveries (
    id CHAR(36) NOT NULL PRIMARY KEY,
    organization_id CHAR(36) NOT NULL,
    integration_id CHAR(36) NOT NULL,
    endpoint_id CHAR(36) NOT NULL,
    verification_id CHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    decision_fingerprint VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    attempt INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    http_status INT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error_code VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_webhook_deliveries_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_webhook_deliveries_integration FOREIGN KEY (integration_id) REFERENCES integrations (id),
    CONSTRAINT fk_webhook_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints (id),
    CONSTRAINT fk_webhook_deliveries_verification FOREIGN KEY (verification_id) REFERENCES verifications (id),
    CONSTRAINT uq_webhook_deliveries_fingerprint UNIQUE (verification_id, event_type, decision_fingerprint)
);

CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries (status, next_attempt_at);
CREATE INDEX idx_webhook_deliveries_endpoint ON webhook_deliveries (endpoint_id, created_at);
