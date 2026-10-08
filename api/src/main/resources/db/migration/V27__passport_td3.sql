INSERT INTO document_definitions (
    id, code, country, document_type, side, mrz_format, enabled, created_at, updated_at)
VALUES (
    '018f5a00-0000-7000-8000-0000000000b1',
    'PASSPORT_TD3',
    'UT',
    'PASSPORT',
    'FRONT',
    'TD3',
    TRUE,
    '2026-10-07 16:00:00',
    '2026-10-07 16:00:00');

INSERT INTO document_definition_versions (
    id, definition_id, version, status, schema_json, activated_at, retired_at, active_marker)
VALUES (
    '018f5a00-0000-7000-8000-0000000000b2',
    '018f5a00-0000-7000-8000-0000000000b1',
    '2024',
    'ACTIVE',
    '{"code":"PASSPORT_TD3","country":"UT","documentType":"PASSPORT","version":"2024","side":"FRONT","mrzFormat":"TD3","fields":[{"name":"lastName","type":"NAME","required":true,"normalizer":"ICAO_NAME"},{"name":"firstName","type":"NAME","required":true,"normalizer":"ICAO_NAME"},{"name":"documentNumber","type":"DOCUMENT_NUMBER","required":true},{"name":"dateOfBirth","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd"]},{"name":"expirationDate","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd"]},{"name":"sex","type":"SEX","required":true},{"name":"nationality","type":"NATIONALITY","required":false},{"name":"mrz","type":"MRZ","required":true}],"rules":[]}',
    '2026-10-07 16:00:00',
    NULL,
    '018f5a00-0000-7000-8000-0000000000b2');

INSERT INTO document_fields (id, version_id, name, value_type, required, field_order, normalizer, formats) VALUES
    ('018f5a00-0000-7000-8000-0000000000b3', '018f5a00-0000-7000-8000-0000000000b2', 'lastName', 'NAME', TRUE, 0, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000b4', '018f5a00-0000-7000-8000-0000000000b2', 'firstName', 'NAME', TRUE, 1, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000b5', '018f5a00-0000-7000-8000-0000000000b2', 'documentNumber', 'DOCUMENT_NUMBER', TRUE, 2, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000b6', '018f5a00-0000-7000-8000-0000000000b2', 'dateOfBirth', 'DATE', TRUE, 3, 'ISO_DATE', 'yyyy-MM-dd'),
    ('018f5a00-0000-7000-8000-0000000000b7', '018f5a00-0000-7000-8000-0000000000b2', 'expirationDate', 'DATE', TRUE, 4, 'ISO_DATE', 'yyyy-MM-dd'),
    ('018f5a00-0000-7000-8000-0000000000b8', '018f5a00-0000-7000-8000-0000000000b2', 'sex', 'SEX', TRUE, 5, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000b9', '018f5a00-0000-7000-8000-0000000000b2', 'nationality', 'NATIONALITY', FALSE, 6, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000ba', '018f5a00-0000-7000-8000-0000000000b2', 'mrz', 'MRZ', TRUE, 7, NULL, NULL);
