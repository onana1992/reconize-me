ALTER TABLE consents
    MODIFY ip_hash VARCHAR(64);

ALTER TABLE verifications
    MODIFY hosted_token_hash VARCHAR(64) NOT NULL;

ALTER TABLE idempotency_keys
    MODIFY request_hash VARCHAR(64) NOT NULL;
