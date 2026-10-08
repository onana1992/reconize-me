INSERT INTO document_definitions (
    id, code, country, document_type, side, mrz_format, enabled, created_at, updated_at)
VALUES (
    '018f5a00-0000-7000-8000-0000000000e1',
    'CANADA_PERMANENT_RESIDENT',
    'CA',
    'RESIDENCE_PERMIT',
    'FRONT',
    'NONE',
    TRUE,
    '2026-10-08 04:10:00',
    '2026-10-08 04:10:00');

INSERT INTO document_definition_versions (
    id, definition_id, version, status, schema_json, activated_at, retired_at, active_marker)
VALUES (
    '018f5a00-0000-7000-8000-0000000000e2',
    '018f5a00-0000-7000-8000-0000000000e1',
    '2015',
    'ACTIVE',
    '{"code":"CANADA_PERMANENT_RESIDENT","country":"CA","documentType":"RESIDENCE_PERMIT","version":"2015","side":"FRONT","mrzFormat":"NONE","fields":[{"name":"lastName","type":"NAME","required":true,"normalizer":"ICAO_NAME","hint":"surname on the Name/Nom line; it may contain two words"},{"name":"firstName","type":"NAME","required":true,"normalizer":"ICAO_NAME","hint":"given names on the line under the surname"},{"name":"documentNumber","type":"DOCUMENT_NUMBER","required":true,"hint":"labeled ID No; this is not the PD number on the back"},{"name":"dateOfBirth","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd"],"hint":"labeled Date of birth; the card prints a bilingual date such as 09 APR /AVR 92; write yyyy-MM-dd, and a past year 92 is 1992"},{"name":"expirationDate","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd"],"hint":"labeled Expiry; write yyyy-MM-dd, and a year 30 is 2030"},{"name":"sex","type":"SEX","required":false,"hint":"labeled Sex"},{"name":"nationality","type":"NATIONALITY","required":false,"hint":"labeled Nationality, alpha-3 such as CMR"}],"rules":[]}',
    '2026-10-08 04:10:00',
    NULL,
    '018f5a00-0000-7000-8000-0000000000e2');

INSERT INTO document_fields (id, version_id, name, value_type, required, field_order, normalizer, formats) VALUES
    ('018f5a00-0000-7000-8000-0000000000e5', '018f5a00-0000-7000-8000-0000000000e2', 'lastName', 'NAME', TRUE, 0, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000e6', '018f5a00-0000-7000-8000-0000000000e2', 'firstName', 'NAME', TRUE, 1, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000e7', '018f5a00-0000-7000-8000-0000000000e2', 'documentNumber', 'DOCUMENT_NUMBER', TRUE, 2, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000e8', '018f5a00-0000-7000-8000-0000000000e2', 'dateOfBirth', 'DATE', TRUE, 3, 'ISO_DATE', 'yyyy-MM-dd'),
    ('018f5a00-0000-7000-8000-0000000000e9', '018f5a00-0000-7000-8000-0000000000e2', 'expirationDate', 'DATE', TRUE, 4, 'ISO_DATE', 'yyyy-MM-dd'),
    ('018f5a00-0000-7000-8000-0000000000ea', '018f5a00-0000-7000-8000-0000000000e2', 'sex', 'SEX', FALSE, 5, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000eb', '018f5a00-0000-7000-8000-0000000000e2', 'nationality', 'NATIONALITY', FALSE, 6, NULL, NULL);

INSERT INTO document_definitions (
    id, code, country, document_type, side, mrz_format, enabled, created_at, updated_at)
VALUES (
    '018f5a00-0000-7000-8000-0000000000e3',
    'CANADA_PERMANENT_RESIDENT',
    'CA',
    'RESIDENCE_PERMIT',
    'BACK',
    'TD1',
    TRUE,
    '2026-10-08 04:10:00',
    '2026-10-08 04:10:00');

INSERT INTO document_definition_versions (
    id, definition_id, version, status, schema_json, activated_at, retired_at, active_marker)
VALUES (
    '018f5a00-0000-7000-8000-0000000000e4',
    '018f5a00-0000-7000-8000-0000000000e3',
    '2015',
    'ACTIVE',
    '{"code":"CANADA_PERMANENT_RESIDENT","country":"CA","documentType":"RESIDENCE_PERMIT","version":"2015","side":"BACK","mrzFormat":"TD1","fields":[{"name":"documentNumber","type":"DOCUMENT_NUMBER","required":true,"hint":"the PD number printed above the machine-readable zone, such as PD4077908; do not use the front ID No"},{"name":"lastName","type":"NAME","required":false,"normalizer":"ICAO_NAME","hint":"surname from the third MRZ line, before the double filler"},{"name":"firstName","type":"NAME","required":false,"normalizer":"ICAO_NAME","hint":"given names from the third MRZ line, after the double filler"},{"name":"dateOfBirth","type":"DATE","required":false,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd"],"hint":"birth date from the MRZ; write yyyy-MM-dd"},{"name":"expirationDate","type":"DATE","required":false,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd"],"hint":"expiry date from the MRZ; write yyyy-MM-dd, and a year 30 is 2030"},{"name":"sex","type":"SEX","required":false,"hint":"sex letter from the MRZ"},{"name":"nationality","type":"NATIONALITY","required":false,"hint":"nationality alpha-3 from the MRZ, such as CMR"},{"name":"mrz","type":"MRZ","required":true,"hint":"the three lines of 30 characters at the bottom of the back"},{"name":"placeOfLanding","type":"STRING","required":false,"hint":"labeled Place of landing"},{"name":"residentSince","type":"DATE","required":false,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd"],"hint":"labeled PR Since; write yyyy-MM-dd"},{"name":"eyeColor","type":"STRING","required":false,"hint":"labeled Eyes"},{"name":"heightCm","type":"STRING","required":false,"hint":"labeled Height, digits only"},{"name":"countryOfBirth","type":"NATIONALITY","required":false,"hint":"labeled COB, alpha-3 such as CMR"}],"rules":[]}',
    '2026-10-08 04:10:00',
    NULL,
    '018f5a00-0000-7000-8000-0000000000e4');

INSERT INTO document_fields (id, version_id, name, value_type, required, field_order, normalizer, formats) VALUES
    ('018f5a00-0000-7000-8000-0000000000ec', '018f5a00-0000-7000-8000-0000000000e4', 'documentNumber', 'DOCUMENT_NUMBER', TRUE, 0, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000ed', '018f5a00-0000-7000-8000-0000000000e4', 'lastName', 'NAME', FALSE, 1, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000ee', '018f5a00-0000-7000-8000-0000000000e4', 'firstName', 'NAME', FALSE, 2, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000ef', '018f5a00-0000-7000-8000-0000000000e4', 'dateOfBirth', 'DATE', FALSE, 3, 'ISO_DATE', 'yyyy-MM-dd'),
    ('018f5a00-0000-7000-8000-0000000000f0', '018f5a00-0000-7000-8000-0000000000e4', 'expirationDate', 'DATE', FALSE, 4, 'ISO_DATE', 'yyyy-MM-dd'),
    ('018f5a00-0000-7000-8000-0000000000f1', '018f5a00-0000-7000-8000-0000000000e4', 'sex', 'SEX', FALSE, 5, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000f2', '018f5a00-0000-7000-8000-0000000000e4', 'nationality', 'NATIONALITY', FALSE, 6, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000f3', '018f5a00-0000-7000-8000-0000000000e4', 'mrz', 'MRZ', TRUE, 7, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000f4', '018f5a00-0000-7000-8000-0000000000e4', 'placeOfLanding', 'STRING', FALSE, 8, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000f5', '018f5a00-0000-7000-8000-0000000000e4', 'residentSince', 'DATE', FALSE, 9, 'ISO_DATE', 'yyyy-MM-dd'),
    ('018f5a00-0000-7000-8000-0000000000f6', '018f5a00-0000-7000-8000-0000000000e4', 'eyeColor', 'STRING', FALSE, 10, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000f7', '018f5a00-0000-7000-8000-0000000000e4', 'heightCm', 'STRING', FALSE, 11, NULL, NULL),
    ('018f5a00-0000-7000-8000-0000000000f8', '018f5a00-0000-7000-8000-0000000000e4', 'countryOfBirth', 'NATIONALITY', FALSE, 12, NULL, NULL);
