UPDATE document_definition_versions
SET schema_json = REPLACE(
    REPLACE(
        schema_json,
        'date labeled Expire le',
        'the date printed immediately after Expire le, the right-hand date on the bottom line shared with Valide le; never use the Valide le date'),
    'date labeled Valide le',
    'the date printed immediately after Valide le, the left-hand date on the bottom line shared with Expire le; do not copy it into expirationDate')
WHERE id = '018f5a00-0000-7000-8000-0000000000a2';
