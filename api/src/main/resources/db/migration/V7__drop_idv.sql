-- Retrait du produit IDV. Les comptes, clés et audit restent.
-- Consents d'abord (FK vers verifications).

DROP TABLE IF EXISTS consents;
DROP TABLE IF EXISTS idempotency_keys;
DROP TABLE IF EXISTS verifications;
