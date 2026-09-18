CREATE TABLE credit_accounts (
    organization_id      CHAR(36)     NOT NULL,
    currency             CHAR(3)      NOT NULL,
    balance_minor        BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (organization_id),
    CONSTRAINT fk_credit_accounts_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

INSERT INTO credit_accounts (organization_id, currency, balance_minor, created_at, updated_at)
SELECT id, 'usd', 0, created_at, created_at
FROM organizations;

CREATE TABLE credit_ledger_entries (
    id                   CHAR(36)     NOT NULL,
    organization_id      CHAR(36)     NOT NULL,
    entry_type           VARCHAR(16)  NOT NULL,
    amount_minor         BIGINT       NOT NULL,
    balance_after_minor  BIGINT       NOT NULL,
    product              VARCHAR(32)  NULL,
    resource_type        VARCHAR(32)  NULL,
    resource_id          CHAR(36)     NULL,
    stripe_event_id      VARCHAR(64)  NULL,
    stripe_checkout_session_id VARCHAR(128) NULL,
    created_by_user_id   CHAR(36)     NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ledger_stripe_event (stripe_event_id),
    UNIQUE KEY uq_ledger_checkout (stripe_checkout_session_id),
    UNIQUE KEY uq_ledger_resource (resource_type, resource_id),
    KEY idx_ledger_org_created (organization_id, created_at),
    CONSTRAINT fk_ledger_org FOREIGN KEY (organization_id) REFERENCES credit_accounts (organization_id)
);

CREATE TABLE stripe_customers (
    organization_id      CHAR(36)     NOT NULL,
    stripe_customer_id   VARCHAR(128) NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (organization_id),
    UNIQUE KEY uq_stripe_customer_id (stripe_customer_id),
    CONSTRAINT fk_stripe_customers_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);
