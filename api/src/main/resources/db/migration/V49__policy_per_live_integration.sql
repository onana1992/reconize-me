ALTER TABLE idv_policies ADD COLUMN integration_id CHAR(36) NULL;
ALTER TABLE aml_policies ADD COLUMN integration_id CHAR(36) NULL;

UPDATE idv_policies AS p
SET integration_id = live.id::text
FROM (
    SELECT DISTINCT ON (organization_id) organization_id, id
    FROM integrations
    WHERE product = 'IDENTITY' AND mode = 'LIVE'
    ORDER BY organization_id, created_at, id
) AS live
WHERE live.organization_id = p.organization_id;

UPDATE aml_policies AS p
SET integration_id = live.id::text
FROM (
    SELECT DISTINCT ON (organization_id) organization_id, id
    FROM integrations
    WHERE product = 'AML' AND mode = 'LIVE'
    ORDER BY organization_id, created_at, id
) AS live
WHERE live.organization_id = p.organization_id;

UPDATE idv_policies
SET active = FALSE, active_marker = NULL
WHERE integration_id IS NULL AND active = TRUE;

UPDATE aml_policies
SET active = FALSE, active_marker = NULL
WHERE integration_id IS NULL AND active = TRUE;

UPDATE idv_policies
SET active_marker = integration_id
WHERE active = TRUE AND integration_id IS NOT NULL;

UPDATE aml_policies
SET active_marker = integration_id
WHERE active = TRUE AND integration_id IS NOT NULL;

ALTER TABLE idv_policies DROP CONSTRAINT IF EXISTS uq_idv_policies_org_version;
ALTER TABLE aml_policies DROP CONSTRAINT IF EXISTS uq_aml_policies_org_version;
DROP INDEX IF EXISTS uq_idv_policies_org_version;
DROP INDEX IF EXISTS uq_aml_policies_org_version;

ALTER TABLE idv_policies
    ADD CONSTRAINT uq_idv_policies_integration_version UNIQUE (integration_id, version);
ALTER TABLE aml_policies
    ADD CONSTRAINT uq_aml_policies_integration_version UNIQUE (integration_id, version);

INSERT INTO idv_policies (
    id, organization_id, integration_id, version, active, active_marker,
    expired_outcome, attempts_outcome, ocr_review_at, liveness_pass_at,
    face_match_fail_at, face_match_pass_at, link_ttl_seconds,
    max_capture_attempts, max_liveness_attempts,
    screening_enabled, screening_on_match, published_at, published_by)
SELECT
    gen_random_uuid()::text, p.organization_id, i.id::text, p.version, TRUE, i.id::text,
    p.expired_outcome, p.attempts_outcome, p.ocr_review_at, p.liveness_pass_at,
    p.face_match_fail_at, p.face_match_pass_at, p.link_ttl_seconds,
    p.max_capture_attempts, p.max_liveness_attempts,
    p.screening_enabled, p.screening_on_match, p.published_at, p.published_by
FROM idv_policies p
INNER JOIN integrations i
    ON i.organization_id = p.organization_id
    AND i.product = 'IDENTITY'
    AND i.mode = 'LIVE'
    AND i.id::text <> p.integration_id
WHERE p.active = TRUE AND p.integration_id IS NOT NULL;

INSERT INTO idv_policy_documents (policy_id, definition_code)
SELECT c.id, d.definition_code
FROM idv_policies c
INNER JOIN idv_policies o
    ON o.organization_id = c.organization_id
    AND o.version = c.version
    AND o.id <> c.id
INNER JOIN idv_policy_documents d ON d.policy_id = o.id
WHERE NOT EXISTS (
    SELECT 1 FROM idv_policy_documents existing
    WHERE existing.policy_id = c.id AND existing.definition_code = d.definition_code
);

INSERT INTO aml_policies (
    id, organization_id, integration_id, version, active, active_marker,
    review_threshold, confirm_threshold, categories, attach_to_identity, on_match,
    test_integration_id, live_integration_id, published_at, published_by)
SELECT
    gen_random_uuid()::text, p.organization_id, i.id::text, p.version, TRUE, i.id::text,
    p.review_threshold, p.confirm_threshold, p.categories, p.attach_to_identity, p.on_match,
    p.test_integration_id, p.live_integration_id, p.published_at, p.published_by
FROM aml_policies p
INNER JOIN integrations i
    ON i.organization_id = p.organization_id
    AND i.product = 'AML'
    AND i.mode = 'LIVE'
    AND i.id::text <> p.integration_id
WHERE p.active = TRUE AND p.integration_id IS NOT NULL;

ALTER TABLE idv_policies
    ADD CONSTRAINT fk_idv_policies_integration FOREIGN KEY (integration_id) REFERENCES integrations (id);
ALTER TABLE aml_policies
    ADD CONSTRAINT fk_aml_policies_integration FOREIGN KEY (integration_id) REFERENCES integrations (id);

CREATE INDEX idx_idv_policies_integration ON idv_policies (integration_id);
CREATE INDEX idx_aml_policies_integration ON aml_policies (integration_id);
