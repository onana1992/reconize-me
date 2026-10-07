CREATE TABLE document_definitions (
    id              CHAR(36)     NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    country         VARCHAR(2)   NOT NULL,
    document_type   VARCHAR(32)  NOT NULL,
    side            VARCHAR(16)  NOT NULL,
    mrz_format      VARCHAR(8)   NOT NULL,
    enabled         BOOLEAN      NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_document_definitions_code (code)
);

CREATE TABLE document_definition_versions (
    id              CHAR(36)     NOT NULL,
    definition_id   CHAR(36)     NOT NULL,
    version         VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    schema_json     LONGTEXT     NOT NULL,
    activated_at    DATETIME(6)  NULL,
    retired_at      DATETIME(6)  NULL,
    active_marker   CHAR(36)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_document_definition_versions_version (definition_id, version),
    UNIQUE KEY uq_document_definition_versions_active (active_marker),
    CONSTRAINT fk_document_definition_versions_definition
        FOREIGN KEY (definition_id) REFERENCES document_definitions (id)
);

CREATE TABLE document_fields (
    id              CHAR(36)     NOT NULL,
    version_id      CHAR(36)     NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    value_type      VARCHAR(32)  NOT NULL,
    required        BOOLEAN      NOT NULL,
    field_order     INT          NOT NULL,
    normalizer      VARCHAR(32)  NULL,
    formats         VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_document_fields_name (version_id, name),
    CONSTRAINT fk_document_fields_version
        FOREIGN KEY (version_id) REFERENCES document_definition_versions (id)
);

CREATE TABLE document_validation_rules (
    id              CHAR(36)     NOT NULL,
    version_id      CHAR(36)     NOT NULL,
    level           VARCHAR(8)   NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    expression      VARCHAR(255) NOT NULL,
    severity        VARCHAR(16)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_document_validation_rules_version (version_id),
    CONSTRAINT fk_document_validation_rules_version
        FOREIGN KEY (version_id) REFERENCES document_definition_versions (id)
);

INSERT INTO document_definitions (
    id, code, country, document_type, side, mrz_format, enabled, created_at, updated_at)
VALUES (
    '018f5a00-0000-7000-8000-0000000000a1',
    'QUEBEC_DRIVER_LICENSE',
    'CA',
    'DRIVING_LICENSE',
    'FRONT',
    'NONE',
    TRUE,
    '2026-10-06 16:00:00',
    '2026-10-06 16:00:00');

INSERT INTO document_definition_versions (
    id, definition_id, version, status, schema_json, activated_at, retired_at, active_marker)
VALUES (
    '018f5a00-0000-7000-8000-0000000000a2',
    '018f5a00-0000-7000-8000-0000000000a1',
    '2024',
    'ACTIVE',
    '{"code":"QUEBEC_DRIVER_LICENSE","country":"CA","documentType":"DRIVING_LICENSE","version":"2024","side":"FRONT","mrzFormat":"NONE","issuingJurisdiction":"QC","fields":[{"name":"firstName","type":"NAME","required":true,"normalizer":"ICAO_NAME"},{"name":"lastName","type":"NAME","required":true,"normalizer":"ICAO_NAME"},{"name":"dateOfBirth","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd","yyyy/MM/dd","dd/MM/yyyy","MM/dd/yyyy"]},{"name":"expirationDate","type":"DATE","required":true,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd","yyyy/MM/dd","dd/MM/yyyy","MM/dd/yyyy"]},{"name":"documentNumber","type":"DOCUMENT_NUMBER","required":true}],"rules":[]}',
    '2026-10-06 16:00:00',
    NULL,
    '018f5a00-0000-7000-8000-0000000000a1');

INSERT INTO document_fields (id, version_id, name, value_type, required, field_order, normalizer, formats) VALUES
    ('018f5a00-0000-7000-8000-0000000000a3', '018f5a00-0000-7000-8000-0000000000a2', 'firstName', 'NAME', TRUE, 0, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000a4', '018f5a00-0000-7000-8000-0000000000a2', 'lastName', 'NAME', TRUE, 1, 'ICAO_NAME', NULL),
    ('018f5a00-0000-7000-8000-0000000000a5', '018f5a00-0000-7000-8000-0000000000a2', 'dateOfBirth', 'DATE', TRUE, 2, 'ISO_DATE', 'yyyy-MM-dd,yyyy/MM/dd,dd/MM/yyyy,MM/dd/yyyy'),
    ('018f5a00-0000-7000-8000-0000000000a6', '018f5a00-0000-7000-8000-0000000000a2', 'expirationDate', 'DATE', TRUE, 3, 'ISO_DATE', 'yyyy-MM-dd,yyyy/MM/dd,dd/MM/yyyy,MM/dd/yyyy'),
    ('018f5a00-0000-7000-8000-0000000000a7', '018f5a00-0000-7000-8000-0000000000a2', 'documentNumber', 'DOCUMENT_NUMBER', TRUE, 4, NULL, NULL);
