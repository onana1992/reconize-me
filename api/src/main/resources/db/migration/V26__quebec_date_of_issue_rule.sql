UPDATE document_definition_versions
SET schema_json = REPLACE(
        REPLACE(
                schema_json,
                '{"name":"documentNumber","type":"DOCUMENT_NUMBER","required":true}]',
                '{"name":"documentNumber","type":"DOCUMENT_NUMBER","required":true},{"name":"dateOfIssue","type":"DATE","required":false,"normalizer":"ISO_DATE","formats":["yyyy-MM-dd","yyyy/MM/dd","dd/MM/yyyy","MM/dd/yyyy"]}]'),
        '"rules":[]',
        '"rules":[{"level":"L3","code":"DOB_AFTER_ISSUE","expression":"dateOfBirth > dateOfIssue","severity":"ERROR"}]')
WHERE id = '018f5a00-0000-7000-8000-0000000000a2';

INSERT INTO document_fields (id, version_id, name, value_type, required, field_order, normalizer, formats)
VALUES (
    '018f5a00-0000-7000-8000-0000000000a8',
    '018f5a00-0000-7000-8000-0000000000a2',
    'dateOfIssue',
    'DATE',
    FALSE,
    5,
    'ISO_DATE',
    'yyyy-MM-dd,yyyy/MM/dd,dd/MM/yyyy,MM/dd/yyyy');

INSERT INTO document_validation_rules (id, version_id, level, code, expression, severity)
VALUES (
    '018f5a00-0000-7000-8000-0000000000a9',
    '018f5a00-0000-7000-8000-0000000000a2',
    'L3',
    'DOB_AFTER_ISSUE',
    'dateOfBirth > dateOfIssue',
    'ERROR');
