ALTER TABLE document_definitions DROP INDEX uq_document_definitions_code;

ALTER TABLE document_definitions
    ADD UNIQUE KEY uq_document_definitions_code_side (code, side);

UPDATE document_definition_versions
SET schema_json = '{"code":"QUEBEC_DRIVER_LICENSE","country":"CA","documentType":"DRIVING_LICENSE","version":"2024","side":"FRONT","mrzFormat":"NONE","issuingJurisdiction":"QC","fields":[{"name":"firstName","type":"NAME","required":true,"normalizer":"ICAO_NAME","hint":"given names on the line under the surname"},{"name":"lastName","type":"NAME","required":true,"normalizer":"ICAO_NAME","hint":"surname on the line directly under the document number; it may contain two words"},{"name":"dateOfBirth","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd","yyyy/MM/dd","dd/MM/yyyy","MM/dd/yyyy"],"hint":"date labeled Date de naissance"},{"name":"expirationDate","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd","yyyy/MM/dd","dd/MM/yyyy","MM/dd/yyyy"],"hint":"date labeled Expire le"},{"name":"documentNumber","type":"DOCUMENT_NUMBER","required":true,"hint":"large number at the top of the card"},{"name":"dateOfIssue","type":"DATE","required":false,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd","yyyy/MM/dd","dd/MM/yyyy","MM/dd/yyyy"],"hint":"date labeled Valide le"}],"rules":[{"level":"L3","code":"DOB_AFTER_ISSUE","expression":"dateOfBirth > dateOfIssue","severity":"ERROR"}]}'
WHERE id = '018f5a00-0000-7000-8000-0000000000a2';

INSERT INTO document_definitions (
    id, code, country, document_type, side, mrz_format, enabled, created_at, updated_at)
VALUES (
    '018f5a00-0000-7000-8000-0000000000c1',
    'QUEBEC_DRIVER_LICENSE',
    'CA',
    'DRIVING_LICENSE',
    'BACK',
    'NONE',
    TRUE,
    '2026-10-07 20:00:00',
    '2026-10-07 20:00:00');

INSERT INTO document_definition_versions (
    id, definition_id, version, status, schema_json, activated_at, retired_at, active_marker)
VALUES (
    '018f5a00-0000-7000-8000-0000000000c2',
    '018f5a00-0000-7000-8000-0000000000c1',
    '2024',
    'ACTIVE',
    '{"code":"QUEBEC_DRIVER_LICENSE","country":"CA","documentType":"DRIVING_LICENSE","version":"2024","side":"BACK","mrzFormat":"NONE","issuingJurisdiction":"QC","fields":[{"name":"barcode","type":"STRING","required":true,"hint":"PDF417 barcode; value PRESENT when the two-dimensional barcode is visible"},{"name":"classDescription","type":"STRING","required":false,"hint":"line that starts with CLASSE(S)"},{"name":"cardEdition","type":"STRING","required":false,"hint":"edition printed in parentheses such as 2024-01"}],"rules":[]}',
    '2026-10-07 20:00:00',
    NULL,
    '018f5a00-0000-7000-8000-0000000000c2');

INSERT INTO document_fields (id, version_id, name, value_type, required, field_order, normalizer, formats) VALUES
    ('018f5a00-0000-7000-8000-0000000000c3', '018f5a00-0000-7000-8000-0000000000c2', 'barcode', 'STRING', TRUE, 0, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000c4', '018f5a00-0000-7000-8000-0000000000c2', 'classDescription', 'STRING', FALSE, 1, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000c5', '018f5a00-0000-7000-8000-0000000000c2', 'cardEdition', 'STRING', FALSE, 2, NULL, NULL);

ALTER TABLE verification_document_analyses
    ADD COLUMN side VARCHAR(16) NOT NULL DEFAULT 'FRONT';

ALTER TABLE verification_document_analyses DROP FOREIGN KEY fk_verification_document_analyses_verification;
ALTER TABLE verification_document_analyses DROP FOREIGN KEY fk_verification_document_analyses_schema;

ALTER TABLE verification_document_analyses
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (verification_id, side);

ALTER TABLE verification_document_analyses
    ADD CONSTRAINT fk_verification_document_analyses_verification
        FOREIGN KEY (verification_id) REFERENCES verifications (id);

ALTER TABLE verification_document_analyses
    ADD CONSTRAINT fk_verification_document_analyses_schema
        FOREIGN KEY (schema_version_id) REFERENCES document_definition_versions (id);

ALTER TABLE verifications
    ADD COLUMN document_back_required BOOLEAN NOT NULL DEFAULT FALSE;
