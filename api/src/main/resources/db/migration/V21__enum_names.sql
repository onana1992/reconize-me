UPDATE verifications SET status = UPPER(status);
UPDATE verifications SET decision = UPPER(decision) WHERE decision IS NOT NULL;

UPDATE verification_media SET kind = UPPER(kind), status = UPPER(status);
UPDATE verification_signals SET outcome = UPPER(outcome);
UPDATE consents SET decision = UPPER(decision);

UPDATE memberships SET status = UPPER(status);

UPDATE integrations SET product = UPPER(product), mode = UPPER(mode);

UPDATE webhook_endpoints SET status = UPPER(status);
UPDATE webhook_deliveries SET status = UPPER(status);
UPDATE webhook_deliveries
SET event_type = 'VERIFICATION_COMPLETED'
WHERE event_type IN ('verification.completed', 'VERIFICATION.COMPLETED');

UPDATE credit_ledger_entries SET entry_type = UPPER(entry_type);
UPDATE credit_ledger_entries SET product = UPPER(product) WHERE product IS NOT NULL;
UPDATE credit_ledger_entries SET resource_type = UPPER(resource_type) WHERE resource_type IS NOT NULL;
