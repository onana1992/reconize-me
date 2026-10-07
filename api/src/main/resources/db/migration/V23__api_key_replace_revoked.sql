ALTER TABLE api_keys
    ADD KEY idx_api_keys_integration (integration_id),
    DROP INDEX uq_api_keys_integration;

ALTER TABLE api_keys
    ADD COLUMN active_integration_id CHAR(36)
        GENERATED ALWAYS AS (CASE WHEN revoked = 0 THEN integration_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_api_keys_active_integration (active_integration_id);
