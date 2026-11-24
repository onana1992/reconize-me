ALTER TABLE authentication_idempotency_keys
    ALTER COLUMN request_hash TYPE VARCHAR(64);

ALTER TABLE authentications
    ALTER COLUMN hosted_token_hash TYPE VARCHAR(64);
