DELETE FROM verification_signals;
DELETE FROM verification_media;
DELETE FROM consents;
DELETE FROM idempotency_keys;
DELETE FROM verifications;
DELETE FROM api_keys;
DELETE FROM integrations;

ALTER TABLE integrations
    ADD UNIQUE KEY uq_integrations_org_product_name (organization_id, product, name);

ALTER TABLE api_keys
    DROP KEY idx_api_keys_integration,
    ADD UNIQUE KEY uq_api_keys_integration (integration_id);
